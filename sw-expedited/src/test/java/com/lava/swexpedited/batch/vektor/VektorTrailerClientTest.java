package com.lava.swexpedited.batch.vektor;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.lava.swexpedited.vektor.VektorGrpcWeb;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@WireMockTest
class VektorTrailerClientTest {

    private static final String TRAILERS_GET_PATH = "/carrier/dashboard/fleet/envoy/Trailers/Get";

    @Test
    void fetchTrailers_returnsRawDecodedMessages(WireMockRuntimeInfo wireMockRuntimeInfo) {
        VektorGrpcWeb.Writer response = new VektorGrpcWeb.Writer()
                .writeMessage(1, new VektorGrpcWeb.Writer().writeString(1, "trailer-uuid-1"))
                .writeMessage(1, new VektorGrpcWeb.Writer().writeString(1, "trailer-uuid-2"));
        stubFor(post(urlEqualTo(TRAILERS_GET_PATH))
                .withHeader("Authorization", equalTo("Bearer test-jwt"))
                .withHeader("company_id", equalTo("test-company-id"))
                .willReturn(aResponse().withStatus(200).withBody(VektorGrpcWeb.encodeUnaryResponse(response))));

        VektorTrailerClient client =
                new VektorTrailerClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<VektorGrpcWeb.Message> trailers = client.fetchTrailers("test-jwt", "test-company-id");

        assertThat(trailers)
                .extracting(m -> m.getString(1).orElseThrow())
                .containsExactly("trailer-uuid-1", "trailer-uuid-2");
    }

    @Test
    void fetchTrailers_sendsTheConfirmedRequestBody(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlEqualTo(TRAILERS_GET_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer()))));
        VektorTrailerClient client =
                new VektorTrailerClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        client.fetchTrailers("test-jwt", "test-company-id");

        byte[] expectedBody = VektorGrpcWeb.encodeUnaryRequest(new VektorGrpcWeb.Writer().writeVarint(1, 1));
        verify(postRequestedFor(urlEqualTo(TRAILERS_GET_PATH))
                .withHeader("company_id", equalTo("test-company-id"))
                .withRequestBody(binaryEqualTo(expectedBody)));
    }

    @Test
    void fetchTrailers_emptyResponseBody_throwsWithStatusAndGrpcHeadersRatherThanNpe(
            WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlEqualTo(TRAILERS_GET_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("grpc-status", "3")
                        .withHeader("grpc-message", "invalid argument")));

        VektorTrailerClient client =
                new VektorTrailerClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        assertThatThrownBy(() -> client.fetchTrailers("test-jwt", "test-company-id"))
                .isInstanceOf(VektorGrpcWeb.VektorGrpcWebException.class)
                .hasMessageContaining("Trailers/Get")
                .hasMessageContaining("200")
                .hasMessageContaining("grpc-status=3")
                .hasMessageContaining("grpc-message=invalid argument");
    }

    @Test
    void fetchTrailers_retriesOn5xxThenSucceeds(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlEqualTo(TRAILERS_GET_PATH))
                .inScenario("trailers-retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        stubFor(post(urlEqualTo(TRAILERS_GET_PATH))
                .inScenario("trailers-retry")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer()))));

        VektorTrailerClient client =
                new VektorTrailerClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<VektorGrpcWeb.Message> trailers = client.fetchTrailers("test-jwt", "test-company-id");

        assertThat(trailers).isEmpty();
        verify(2, postRequestedFor(urlPathEqualTo(TRAILERS_GET_PATH)));
    }

    private RestClient vektorRestClient(WireMockRuntimeInfo wireMockRuntimeInfo) {
        return RestClient.builder()
                .baseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
                .build();
    }
}
