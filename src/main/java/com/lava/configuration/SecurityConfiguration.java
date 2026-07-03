package com.lava.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, SecurityContextRepository securityContextRepository) throws Exception {
        http.securityContext(context -> context.securityContextRepository(securityContextRepository));
        http.authorizeHttpRequests(auth -> auth.requestMatchers("/api/auth/login", "/api/auth/register")
                .permitAll()
                .anyRequest()
                .authenticated());
        http.exceptionHandling(handling ->
                handling.authenticationEntryPoint((request, response, authException) -> response.sendError(401)));
        // TODO: CSRF is disabled for now since /api/auth/login has no session yet to issue a
        // token against. Revisit with a token-bootstrap endpoint before this app is exposed
        // to a browser client that isn't fully trusted.
        http.csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
