package com.lava.configuration;

import com.lava.boot.autoconfigure.app.CorsProperties;
import com.lava.security.MfaAuthorities;
import com.lava.security.oauth.GithubEmailBackfillOAuth2UserService;
import com.lava.service.JwtService;
import com.lava.web.filter.JwtAuthenticationFilter;
import com.lava.web.oauth.OAuthAuthenticationFailureHandler;
import com.lava.web.oauth.OAuthAuthenticationSuccessHandler;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationManagerFactories;
import org.springframework.security.authorization.AuthorizationManagerFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE, "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            CorsConfigurationSource corsConfigurationSource,
            GithubEmailBackfillOAuth2UserService githubEmailBackfillOAuth2UserService,
            OAuthAuthenticationSuccessHandler oAuthAuthenticationSuccessHandler,
            OAuthAuthenticationFailureHandler oAuthAuthenticationFailureHandler)
            throws Exception {
        http.addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        // Layers an additional "both factors present" requirement on top of the normal
        // authenticated() check, but only for principals whose token carries the MFA_ENROLLED
        // marker authority (i.e. only for users who actually enrolled TOTP) - see
        // AuthorizationManagerFactories.multiFactor()'s conditional-MFA support. Deliberately kept
        // as a local variable rather than a @Bean: registering an AuthorizationManagerFactory as a
        // bean makes Spring Security wire it in as the GLOBAL default for every plain
        // authenticated()/hasRole() DSL call in the filter chain, not just calls made explicitly
        // through this instance - which would silently gate /api/auth/mfa/verify too.
        AuthorizationManagerFactory<Object> mfaAuthorizationManagerFactory =
                AuthorizationManagerFactories.<Object>multiFactor()
                        .requireFactors(FactorGrantedAuthority.PASSWORD_AUTHORITY, MfaAuthorities.TOTP_FACTOR_AUTHORITY)
                        .when(authentication -> authentication.getAuthorities().stream()
                                .anyMatch(authority ->
                                        MfaAuthorities.MFA_ENROLLED_AUTHORITY.equals(authority.getAuthority())))
                        .build();

        http.authorizeHttpRequests(auth -> auth.requestMatchers(
                        "/api/auth/login",
                        "/api/auth/register/start",
                        "/api/auth/register/verify-code",
                        "/api/auth/register/complete",
                        "/api/auth/refresh",
                        "/oauth2/authorization/**",
                        "/login/oauth2/code/**",
                        // Spring Boot's default error handling forwards internally to /error when
                        // a filter calls response.sendError(...), and Spring Security re-runs the
                        // filter chain for that forwarded dispatch. Without this, a denial (e.g.
                        // the MFA anyRequest() rule below returning 403) reaches /error as an
                        // anonymous request, gets denied there too, and our authenticationEntryPoint
                        // overwrites the still-uncommitted response with 401 - masking the real
                        // status. /error must stay permitAll so it only ever renders whatever
                        // status was already set, never reprocesses authorization itself.
                        "/error")
                .permitAll()
                // Reachable on the password-only-factor token /login issues for an MFA-enrolled
                // user - this endpoint is precisely how that token is upgraded to carry the TOTP
                // factor, so it must not itself require that factor already be present.
                .requestMatchers("/api/auth/mfa/verify")
                .authenticated()
                .anyRequest()
                .access(mfaAuthorizationManagerFactory.authenticated()));

        http.cors(cors -> cors.configurationSource(corsConfigurationSource));

        // csrf.spa() gives sensible defaults for a single-page-app frontend: a cookie-based
        // CSRF token (XSRF-TOKEN, readable by JS) and a request handler that resolves the raw
        // token value directly, matching Angular's built-in interceptor conventions. Do not
        // chain a custom csrfTokenRequestHandler on top of this - spa() already installs the
        // handler that expects the raw cookie value to be echoed back as-is; a
        // XorCsrfTokenRequestAttributeHandler expects a masked value instead and rejects every
        // raw token as invalid.
        http.csrf(CsrfConfigurer::spa);

        // This is a pure JSON API (no template engine, empty static/templates dirs) sitting
        // behind a separate SPA, so the strictest possible CSP is correct here - nothing is ever
        // legitimately loaded as script/style/image/etc. from this origin. This also hardens
        // Spring Boot's default Whitelabel error page, the one place this app can still render
        // HTML (a browser-navigation request with Accept: text/html that falls through to
        // /error). frame-ancestors 'none' is the CSP-level equivalent of Spring Security's
        // default X-Frame-Options: DENY, kept for browsers that prefer CSP over the legacy
        // header. Referrer-Policy isn't set by Spring Security by default; no-referrer is safe
        // here since nothing about this API's URLs needs to reach the OAuth2 provider or the
        // post-login SPA redirect target.
        //
        // Deliberately NOT adding Cross-Origin-Resource-Policy/-Opener-Policy/-Embedder-Policy:
        // CORP: same-origin (a common hardening default) would break the legitimate cross-origin
        // fetch from the Angular SPA, since CORP blocks cross-origin loading independently of the
        // CORS headers above. COOP/COEP are for browsing-context isolation (e.g. SharedArrayBuffer
        // use cases) and aren't relevant to a JSON auth API.
        http.headers(headers -> headers.contentSecurityPolicy(
                        csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));

        http.exceptionHandling(handling ->
                handling.authenticationEntryPoint((request, response, authException) -> response.sendError(401)));

        http.oauth2Login(
                oauth2 -> oauth2.userInfoEndpoint(info -> info.userService(githubEmailBackfillOAuth2UserService))
                        .successHandler(oAuthAuthenticationSuccessHandler)
                        .failureHandler(oAuthAuthenticationFailureHandler));

        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
}
