package com.lava.swexpedited.configuration;

import com.lava.swexpedited.boot.autoconfigure.app.AuthProperties;
import com.lava.swexpedited.boot.autoconfigure.app.CorsProperties;
import com.lava.swexpedited.security.CookieBearerTokenResolver;
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
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
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

    /**
     * Fetches and caches auth-lava's public key from its JWKS endpoint - no signing key is ever shared between the two
     * services, only this public key, so this service can verify tokens but never mint them.
     */
    @Bean
    public JwtDecoder jwtDecoder(AuthProperties authProperties) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withJwkSetUri(authProperties.jwksUri()).build();

        OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(authProperties.issuer());
        decoder.setJwtValidator(validator);

        return decoder;
    }

    @Bean
    public CookieBearerTokenResolver cookieBearerTokenResolver() {
        return new CookieBearerTokenResolver();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // auth-lava's access tokens carry an `authorities` claim shaped like
        // ["ROLE_MEMBER", "MFA_ENROLLED", ...], not an OAuth2 `scope`/`scp` claim, so the default
        // converter (which reads scope claims and prefixes with SCOPE_) has to be reconfigured to
        // match - mirroring what JwtAuthenticationFilter.buildPrincipal reads in auth-lava itself.
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("authorities");
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            CookieBearerTokenResolver cookieBearerTokenResolver,
            CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health", "/error")
                .permitAll()
                .anyRequest()
                .authenticated());

        http.oauth2ResourceServer(oauth2 -> oauth2.bearerTokenResolver(cookieBearerTokenResolver)
                .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter)));

        http.cors(cors -> cors.configurationSource(corsConfigurationSource));

        // Matches auth-lava's SecurityConfiguration - the same Angular SPA sits in front of both
        // services and already sends the XSRF-TOKEN header per csrf.spa()'s conventions.
        http.csrf(CsrfConfigurer::spa);

        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
}
