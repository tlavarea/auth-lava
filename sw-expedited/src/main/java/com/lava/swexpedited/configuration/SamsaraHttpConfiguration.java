package com.lava.swexpedited.configuration;

import com.lava.swexpedited.boot.autoconfigure.app.SamsaraProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * A dedicated RestClient for Samsara's fleet API. Unlike {@link GfmHttpConfiguration}, this needs no custom request
 * factory or cookie store - auth is a single static bearer token attached as a default header, so the injected
 * RestClient.Builder (auto-configured by the {@code spring-boot-starter-restclient} dependency, which centralizes
 * timeout/connector settings via {@code spring.http.clients.*}) is used as-is rather than overridden, per the project's
 * established gotcha that calling .requestFactory(...) on an injected RestClient.Builder silently breaks
 * MockRestServiceServer-based tests.
 */
@Configuration
public class SamsaraHttpConfiguration {

    @Bean(name = "samsaraRestClient")
    public RestClient samsaraRestClient(RestClient.Builder builder, SamsaraProperties samsaraProperties) {
        return builder.baseUrl(samsaraProperties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + samsaraProperties.apiToken())
                .build();
    }
}
