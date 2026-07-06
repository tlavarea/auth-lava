package com.lava.configuration;

import com.lava.boot.autoconfigure.app.CorsProperties;
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
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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

        http.authorizeHttpRequests(auth -> auth.requestMatchers(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/refresh",
                        "/oauth2/authorization/**",
                        "/login/oauth2/code/**")
                .permitAll()
                .anyRequest()
                .authenticated());

        http.cors(cors -> cors.configurationSource(corsConfigurationSource));

        // csrf.spa() gives sensible defaults for a single-page-app frontend: a cookie-based
        // CSRF token (XSRF-TOKEN, readable by JS) and a request handler that resolves the raw
        // token value directly, matching Angular's built-in interceptor conventions. Do not
        // chain a custom csrfTokenRequestHandler on top of this - spa() already installs the
        // handler that expects the raw cookie value to be echoed back as-is; a
        // XorCsrfTokenRequestAttributeHandler expects a masked value instead and rejects every
        // raw token as invalid.
        http.csrf(CsrfConfigurer::spa);

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
