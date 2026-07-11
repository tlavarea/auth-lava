package com.lava.configuration;

import java.security.SecureRandom;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class EncoderConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder(
                "argon2",
                Map.of(
                        "bcrypt",
                        new BCryptPasswordEncoder(),
                        "argon2",
                        Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }

    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }
}
