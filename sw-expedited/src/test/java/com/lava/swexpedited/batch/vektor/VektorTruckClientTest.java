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
class VektorTruckClientTest {

    private static final String TRUCKS_GET_PATH = "/carrier/dashboard/fleet/envoy/Trucks/Get";

    @Test
    void fetchTrucks_returnsRawDecodedMessages(WireMockRuntimeInfo wireMockRuntimeInfo) {
        VektorGrpcWeb.Writer response = new VektorGrpcWeb.Writer()
                .writeMessage(1, new VektorGrpcWeb.Writer().writeString(1, "truck-uuid-1"))
                .writeMessage(1, new VektorGrpcWeb.Writer().writeString(1, "truck-uuid-2"));
        stubFor(post(urlEqualTo(TRUCKS_GET_PATH))
                .withHeader("Authorization", equalTo("Bearer test-jwt"))
                .withHeader("company_id", equalTo("test-company-id"))
                .willReturn(aResponse().withStatus(200).withBody(VektorGrpcWeb.encodeUnaryResponse(response))));

        VektorTruckClient client = new VektorTruckClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<VektorGrpcWeb.Message> trucks = client.fetchTrucks("test-jwt", "test-company-id");

        assertThat(trucks)
                .extracting(m -> m.getString(1).orElseThrow())
                .containsExactly("truck-uuid-1", "truck-uuid-2");
    }

    @Test
    void fetchTrucks_sendsTheConfirmedRequestBody(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlEqualTo(TRUCKS_GET_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer()))));
        VektorTruckClient client = new VektorTruckClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        client.fetchTrucks("test-jwt", "test-company-id");

        byte[] expectedBody = VektorGrpcWeb.encodeUnaryRequest(new VektorGrpcWeb.Writer().writeVarint(1, 1));
        verify(postRequestedFor(urlEqualTo(TRUCKS_GET_PATH))
                .withHeader("company_id", equalTo("test-company-id"))
                .withRequestBody(binaryEqualTo(expectedBody)));
    }

    @Test
    void fetchTrucks_emptyResponseBody_throwsWithStatusAndGrpcHeadersRatherThanNpe(
            WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlEqualTo(TRUCKS_GET_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("grpc-status", "3")
                        .withHeader("grpc-message", "invalid argument")));

        VektorTruckClient client = new VektorTruckClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        assertThatThrownBy(() -> client.fetchTrucks("test-jwt", "test-company-id"))
                .isInstanceOf(VektorGrpcWeb.VektorGrpcWebException.class)
                .hasMessageContaining("Trucks/Get")
                .hasMessageContaining("200")
                .hasMessageContaining("grpc-status=3")
                .hasMessageContaining("grpc-message=invalid argument");
    }

    @Test
    void fetchTrucks_retriesOn5xxThenSucceeds(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlEqualTo(TRUCKS_GET_PATH))
                .inScenario("trucks-retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        stubFor(post(urlEqualTo(TRUCKS_GET_PATH))
                .inScenario("trucks-retry")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer()))));

        VektorTruckClient client = new VektorTruckClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<VektorGrpcWeb.Message> trucks = client.fetchTrucks("test-jwt", "test-company-id");

        assertThat(trucks).isEmpty();
        verify(2, postRequestedFor(urlPathEqualTo(TRUCKS_GET_PATH)));
    }

    private RestClient vektorRestClient(WireMockRuntimeInfo wireMockRuntimeInfo) {
        return RestClient.builder()
                .baseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
                .build();
    }
}
