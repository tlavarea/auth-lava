package com.lava.repository;

import com.lava.configuration.JooqCustomizerConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication(exclude = OAuth2ClientAutoConfiguration.class)
@Import(JooqCustomizerConfiguration.class)
class RepositoryTestConfiguration {}
