package com.lava.swexpedited.batch;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.lava.swexpedited.vektor.VektorGrpcWeb;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@WireMockTest
class VektorManifestClientTest {

    private static final String MANIFESTS_GET_PATH = "/carrier/dashboard/core/envoy/Manifests/Get";

    @Test
    void fetchManifests_parsesRepeatedManifestsFromResponse(WireMockRuntimeInfo wireMockRuntimeInfo) {
        VektorGrpcWeb.Writer response = new VektorGrpcWeb.Writer()
                .writeMessage(3, new VektorGrpcWeb.Writer().writeVarint(2, 1000587L))
                .writeMessage(3, new VektorGrpcWeb.Writer().writeVarint(2, 1000589L));
        stubFor(post(urlEqualTo(MANIFESTS_GET_PATH))
                .withHeader("Authorization", equalTo("Bearer test-jwt"))
                .withHeader("company_id", equalTo("test-company-id"))
                .willReturn(aResponse().withStatus(200).withBody(VektorGrpcWeb.encodeUnaryResponse(response))));

        VektorManifestClient client =
                new VektorManifestClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<VektorGrpcWeb.Message> manifests =
                client.fetchManifests("test-jwt", "test-company-id", List.of("manifest_in_progress"));

        assertThat(manifests).extracting(m -> m.getVarint(2).orElseThrow()).containsExactly(1000587L, 1000589L);
    }

    @Test
    void fetchManifests_emptyResponseBody_throwsWithStatusAndGrpcHeadersRatherThanNpe(
            WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlEqualTo(MANIFESTS_GET_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("grpc-status", "3")
                        .withHeader("grpc-message", "invalid argument")));

        VektorManifestClient client =
                new VektorManifestClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        assertThatThrownBy(() -> client.fetchManifests("test-jwt", "test-company-id", List.of("manifest_in_progress")))
                .isInstanceOf(VektorGrpcWeb.VektorGrpcWebException.class)
                .hasMessageContaining("Manifests/Get")
                .hasMessageContaining("200")
                .hasMessageContaining("grpc-status=3")
                .hasMessageContaining("grpc-message=invalid argument");
    }

    @Test
    void fetchManifests_sendsTheConfirmedFilterRequestShape(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlEqualTo(MANIFESTS_GET_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer()))));
        VektorManifestClient client =
                new VektorManifestClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        client.fetchManifests("test-jwt", "test-company-id", List.of("manifest_in_progress"));

        VektorGrpcWeb.Writer statusValues = new VektorGrpcWeb.Writer().writeString(1, "manifest_in_progress");
        VektorGrpcWeb.Writer statusValuesWrapper = new VektorGrpcWeb.Writer().writeMessage(1, statusValues);
        VektorGrpcWeb.Writer filter = new VektorGrpcWeb.Writer()
                .writeString(1, "effective_status")
                .writeString(2, "is")
                .writeMessage(3, statusValuesWrapper);
        byte[] expectedBody = VektorGrpcWeb.encodeUnaryRequest(new VektorGrpcWeb.Writer()
                .writeVarint(2, 50)
                .writeString(3, "first_stop_appointment_start_datetime")
                .writeString(4, "desc")
                .writeMessage(5, filter));

        verify(postRequestedFor(urlEqualTo(MANIFESTS_GET_PATH))
                .withHeader("company_id", equalTo("test-company-id"))
                .withRequestBody(binaryEqualTo(expectedBody)));
    }

    private RestClient vektorRestClient(WireMockRuntimeInfo wireMockRuntimeInfo) {
        return RestClient.builder()
                .baseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
                .build();
    }
}
