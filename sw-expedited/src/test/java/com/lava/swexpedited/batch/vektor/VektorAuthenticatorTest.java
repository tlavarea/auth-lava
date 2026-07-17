package com.lava.swexpedited.batch.vektor;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.lava.swexpedited.boot.autoconfigure.app.VektorProperties;
import com.lava.swexpedited.vektor.VektorGrpcWeb;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@WireMockTest
class VektorAuthenticatorTest {

    private static final String LOGIN_PATH =
            "/carrier/dashboard/auth/envoy/VerificationCodeService/VerificationCodeSend";

    @Test
    void authenticate_postsCredentialsAndReturnsJwtFromResponse(WireMockRuntimeInfo wireMockRuntimeInfo) {
        byte[] responseBody = VektorGrpcWeb.encodeUnaryResponse(
                new VektorGrpcWeb.Writer().writeVarint(1, 2).writeString(2, "test.jwt.token"));
        stubFor(post(urlEqualTo(LOGIN_PATH))
                .willReturn(aResponse().withStatus(200).withBody(responseBody)));

        VektorAuthenticator authenticator =
                new VektorAuthenticator(vektorRestClient(wireMockRuntimeInfo), vektorProperties(wireMockRuntimeInfo));

        String jwt = authenticator.authenticate();

        assertThat(jwt).isEqualTo("test.jwt.token");
        byte[] expectedRequestBody = VektorGrpcWeb.encodeUnaryRequest(new VektorGrpcWeb.Writer()
                .writeString(1, "user@example.com")
                .writeString(2, "hunter2")
                .writeVarint(3, 1));
        verify(postRequestedFor(urlEqualTo(LOGIN_PATH)).withRequestBody(binaryEqualTo(expectedRequestBody)));
    }

    @Test
    void authenticate_responseWithoutJwtField_throws(WireMockRuntimeInfo wireMockRuntimeInfo) {
        byte[] responseBody = VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer().writeVarint(1, 2));
        stubFor(post(urlEqualTo(LOGIN_PATH))
                .willReturn(aResponse().withStatus(200).withBody(responseBody)));

        VektorAuthenticator authenticator =
                new VektorAuthenticator(vektorRestClient(wireMockRuntimeInfo), vektorProperties(wireMockRuntimeInfo));

        assertThatThrownBy(authenticator::authenticate).isInstanceOf(IllegalStateException.class);
    }

    private VektorProperties vektorProperties(WireMockRuntimeInfo wireMockRuntimeInfo) {
        return new VektorProperties(
                "user@example.com",
                "hunter2",
                "test-company-id",
                wireMockRuntimeInfo.getHttpBaseUrl(),
                Duration.ofMillis(10),
                List.of("manifest_in_progress"));
    }

    private RestClient vektorRestClient(WireMockRuntimeInfo wireMockRuntimeInfo) {
        return RestClient.builder()
                .baseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
                .build();
    }
}
