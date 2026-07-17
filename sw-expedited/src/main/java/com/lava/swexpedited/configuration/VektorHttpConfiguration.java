package com.lava.swexpedited.configuration;

import com.lava.swexpedited.boot.autoconfigure.app.VektorProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * A dedicated RestClient for app.vektortms.com's internal gRPC-Web backend. Unlike {@link GfmHttpConfiguration}, this
 * needs no custom request factory or cookie store: auth is a bearer JWT (obtained per-tasklet-run by
 * {@code VektorAuthenticator} and attached explicitly per request, since it's only known at runtime, not at bean
 * construction time), so the injected RestClient.Builder is used as-is, per the project's established gotcha that
 * calling .requestFactory(...) on an injected RestClient.Builder silently breaks MockRestServiceServer-based tests.
 * Every Vektor RPC shares the same {@code application/grpc-web+proto} content type and {@code x-grpc-web: 1} header
 * (see {@code VektorGrpcWeb}'s javadoc for the wire format), so both are set as defaults here rather than repeated on
 * every call site.
 */
@Configuration
public class VektorHttpConfiguration {

    @Bean(name = "vektorRestClient")
    public RestClient vektorRestClient(RestClient.Builder builder, VektorProperties vektorProperties) {
        return builder.baseUrl(vektorProperties.baseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/grpc-web+proto")
                .defaultHeader("x-grpc-web", "1")
                .build();
    }
}
