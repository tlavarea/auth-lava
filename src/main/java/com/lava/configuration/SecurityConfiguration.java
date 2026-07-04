package com.lava.configuration;

import com.lava.service.JwtService;
import com.lava.web.filter.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http.addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        http.authorizeHttpRequests(
                auth -> auth.requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/refresh")
                        .permitAll()
                        .anyRequest()
                        .authenticated());

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

        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
}
