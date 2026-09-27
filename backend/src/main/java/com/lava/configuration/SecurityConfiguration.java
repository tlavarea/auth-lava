package com.lava.configuration;

import com.lava.boot.autoconfigure.app.CookieProperties;
import com.lava.boot.autoconfigure.app.CorsProperties;
import com.lava.security.MfaAuthorities;
import com.lava.security.oauth.CookieOAuth2AuthorizationRequestRepository;
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
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    /**
     * Apple's authorization callback: a cross-site, top-level form POST from appleid.apple.com, because asking for
     * {@code scope=name email} obliges {@code response_mode=form_post}. Two defences assume a same-site request and are
     * lifted for this one endpoint - CSRF and CORS, below - and both rest on the {@code state} parameter instead.
     */
    static final RequestMatcher APPLE_CALLBACK =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/login/oauth2/code/apple");

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

        // A browser sends `Origin: https://appleid.apple.com` on Apple's form_post callback, and CorsFilter treats any
        // request whose Origin differs from the host as a CORS request - so it checked Apple against allowed-origins
        // and answered 403 "Invalid CORS request" before the OAuth2 filter saw the code. CORS governs whether a
        // script may read a response; this is a navigation, and no script reads anything. Returning no configuration
        // is how CorsFilter is told a request is not its business, which is truer than adding Apple as an allowed
        // origin with credentials on every endpoint.
        return request -> APPLE_CALLBACK.matches(request) ? null : source.getCorsConfiguration(request);
    }

    /**
     * Declared here rather than component-scanned. It is part of this filter chain's configuration and has no
     * collaborators beyond the cookie domain, so the {@code @WebMvcTest} slices that import this class get the real one
     * for free - where a scanned {@code @Component} would have to be mocked into five test classes that never touch
     * OAuth.
     */
    @Bean
    public CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository(
            CookieProperties cookieProperties) {
        return new CookieOAuth2AuthorizationRequestRepository(cookieProperties);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            CorsConfigurationSource corsConfigurationSource,
            GithubEmailBackfillOAuth2UserService githubEmailBackfillOAuth2UserService,
            OAuthAuthenticationSuccessHandler oAuthAuthenticationSuccessHandler,
            OAuthAuthenticationFailureHandler oAuthAuthenticationFailureHandler,
            CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository)
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
                        // The CSRF token for a cross-origin frontend, which must be able to fetch it before it is
                        // signed in - see CsrfController. It reveals nothing a same-origin frontend cannot read
                        // from its own cookie.
                        "/api/auth/csrf",
                        "/oauth2/authorization/**",
                        "/login/oauth2/code/**",
                        // Which OAuth buttons to render. Needed before anyone is authenticated,
                        // and reveals only which /oauth2/authorization/** endpoints are already
                        // public.
                        "/api/auth/providers",
                        // Load balancer / orchestrator health probes hit this unauthenticated -
                        // show-details is "never" (application.yaml) so it only ever reveals UP/DOWN,
                        // never dependency internals, to an anonymous caller.
                        "/actuator/health",
                        // Published for other services to verify JWTs issued by this one - a JWKS
                        // document only ever contains public key material, so it's safe to expose
                        // without authentication (and other services can't authenticate against
                        // this app's session model anyway).
                        "/.well-known/jwks.json",
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
                // factor, so it must not itself require that factor already be present. Logout
                // needs the same carve-out: an MFA-pending session (e.g. one locked out of
                // /mfa/verify by the rate limiter) must still be able to clear its cookies/refresh
                // token instead of being stuck with a live-but-unusable session until it expires.
                .requestMatchers("/api/auth/mfa/verify", "/api/auth/logout")
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
        http.csrf(csrf -> {
            csrf.spa();
            // Apple returns the authorization code as a cross-site form POST, because asking for `scope=name email`
            // obliges `response_mode=form_post`. A browser arriving from appleid.apple.com carries no XSRF-TOKEN, so
            // CsrfFilter would reject the callback before Spring Security's OAuth2 filter ever saw it.
            //
            // Exempting it is not a hole: this endpoint has its own, older CSRF defence in the `state` parameter, which
            // CookieOAuth2AuthorizationRequestRepository holds and OAuth2LoginAuthenticationFilter checks. The matcher
            // is pinned to POST on the apple registration alone, so the GET callbacks Google and GitHub use keep the
            // standard protection.
            csrf.ignoringRequestMatchers(APPLE_CALLBACK);
        });

        http.exceptionHandling(handling ->
                handling.authenticationEntryPoint((request, response, authException) -> response.sendError(401)));

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

        http.oauth2Login(oauth2 -> oauth2
                // Cookie-backed rather than the default HttpSession one. Required for Apple's cross-site POST
                // callback, and it makes the STATELESS policy below true rather than aspirational - see the class
                // javadoc, which is where the reasoning lives.
                .authorizationEndpoint(
                        endpoint -> endpoint.authorizationRequestRepository(authorizationRequestRepository))
                .userInfoEndpoint(info -> info.userService(githubEmailBackfillOAuth2UserService))
                .successHandler(oAuthAuthenticationSuccessHandler)
                .failureHandler(oAuthAuthenticationFailureHandler));

        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
}
