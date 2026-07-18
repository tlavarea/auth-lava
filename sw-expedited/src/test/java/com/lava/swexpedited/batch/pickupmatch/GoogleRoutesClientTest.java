package com.lava.swexpedited.batch.pickupmatch;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.lava.swexpedited.batch.pickupmatch.GoogleRoutesClient.ComputedRoute;
import com.lava.swexpedited.batch.pickupmatch.RouteMatrixClient.LatLng;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@WireMockTest
class GoogleRoutesClientTest {

    private static final String COMPUTE_ROUTES_PATH = "/directions/v2:computeRoutes";

    @Test
    void computeRoute_routeExists_sendsAddressAndLocationWaypointsAndParsesResponse(
            WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlPathEqualTo(COMPUTE_ROUTES_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"routes":[{"distanceMeters":160934,"duration":"7203.500s","polyline":{"encodedPolyline":"abc123"},"legs":[{"startLocation":{"latLng":{"latitude":33.101,"longitude":-87.99}}}]}]}
                                """)));

        GoogleRoutesClient client =
                new GoogleRoutesClient(computeRoutesRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        Optional<ComputedRoute> result =
                client.computeRoute("Bessemer, AL", new LatLng(new BigDecimal("32.735"), new BigDecimal("-97.108")));

        assertThat(result).isPresent();
        ComputedRoute route = result.get();
        assertThat(route.originLatitude()).isEqualByComparingTo(new BigDecimal("33.101"));
        assertThat(route.originLongitude()).isEqualByComparingTo(new BigDecimal("-87.99"));
        assertThat(route.encodedPolyline()).isEqualTo("abc123");
        assertThat(route.distanceMeters()).isEqualTo(160934L);
        assertThat(route.duration()).isEqualTo("7203.500s");

        verify(postRequestedFor(urlPathEqualTo(COMPUTE_ROUTES_PATH)).withRequestBody(equalToJson("""
                        {
                          "origin": {"address": "Bessemer, AL"},
                          "destination": {"location": {"latLng": {"latitude": 32.735, "longitude": -97.108}}},
                          "travelMode": "DRIVE"
                        }
                        """)));
    }

    @Test
    void computeRoute_noRoutesInResponse_returnsEmpty(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlPathEqualTo(COMPUTE_ROUTES_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"routes":[]}
                                """)));

        GoogleRoutesClient client =
                new GoogleRoutesClient(computeRoutesRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        Optional<ComputedRoute> result =
                client.computeRoute("Nowhere", new LatLng(new BigDecimal("0"), new BigDecimal("0")));

        assertThat(result).isEmpty();
    }

    @Test
    void computeRoute_retriesOn5xxThenSucceeds(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlPathEqualTo(COMPUTE_ROUTES_PATH))
                .inScenario("compute-routes-retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        stubFor(post(urlPathEqualTo(COMPUTE_ROUTES_PATH))
                .inScenario("compute-routes-retry")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"routes":[]}
                                """)));

        GoogleRoutesClient client =
                new GoogleRoutesClient(computeRoutesRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        Optional<ComputedRoute> result =
                client.computeRoute("Bessemer, AL", new LatLng(new BigDecimal("0"), new BigDecimal("0")));

        assertThat(result).isEmpty();
        verify(2, postRequestedFor(urlPathEqualTo(COMPUTE_ROUTES_PATH)));
    }

    private RestClient computeRoutesRestClient(WireMockRuntimeInfo wireMockRuntimeInfo) {
        return RestClient.builder()
                .baseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
                .build();
    }
}
