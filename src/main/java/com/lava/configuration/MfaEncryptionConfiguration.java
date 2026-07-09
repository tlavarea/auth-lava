package com.lava.configuration;

import com.lava.boot.autoconfigure.app.MfaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

@Configuration
public class MfaEncryptionConfiguration {

    @Bean
    public TextEncryptor totpSecretEncryptor(MfaProperties mfaProperties) {
        return Encryptors.delux(mfaProperties.encryptionKey(), mfaProperties.encryptionSalt());
    }
}
