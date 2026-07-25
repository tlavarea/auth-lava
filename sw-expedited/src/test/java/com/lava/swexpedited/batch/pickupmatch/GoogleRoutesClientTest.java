package com.lava.swexpedited.batch.pickupmatch;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.lava.swexpedited.batch.pickupmatch.GoogleRoutesClient.ComputedRoute;
import com.lava.swexpedited.batch.pickupmatch.GoogleRoutesClient.RouteWaypoint;
import com.lava.swexpedited.batch.pickupmatch.RouteMatrixClient.LatLng;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@WireMockTest
class GoogleRoutesClientTest {

    private static final String COMPUTE_ROUTES_PATH = "/directions/v2:computeRoutes";
    private static final LatLng ORIGIN = new LatLng(new BigDecimal("33.393"), new BigDecimal("-86.930"));
    private static final LatLng INTERMEDIATE = new LatLng(new BigDecimal("34.0"), new BigDecimal("-111.0"));
    private static final LatLng DESTINATION = new LatLng(new BigDecimal("32.735"), new BigDecimal("-97.108"));

    @Test
    void computeRoute_twoWaypoints_sendsOriginAndDestinationWithNoIntermediatesAndParsesResponse(
            WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlPathEqualTo(COMPUTE_ROUTES_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"routes":[{"distanceMeters":160934,"duration":"7203.500s","polyline":{"encodedPolyline":"abc123"}}]}
                                """)));

        GoogleRoutesClient client =
                new GoogleRoutesClient(computeRoutesRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        Optional<ComputedRoute> result =
                client.computeRoute(List.of(RouteWaypoint.ofLocation(ORIGIN), RouteWaypoint.ofLocation(DESTINATION)));

        assertThat(result).isPresent();
        ComputedRoute route = result.get();
        assertThat(route.encodedPolyline()).isEqualTo("abc123");
        assertThat(route.distanceMeters()).isEqualTo(160934L);
        assertThat(route.duration()).isEqualTo("7203.500s");

        verify(postRequestedFor(urlPathEqualTo(COMPUTE_ROUTES_PATH)).withRequestBody(equalToJson("""
                        {
                          "origin": {"location": {"latLng": {"latitude": 33.393, "longitude": -86.930}}},
                          "destination": {"location": {"latLng": {"latitude": 32.735, "longitude": -97.108}}},
                          "intermediates": [],
                          "travelMode": "DRIVE"
                        }
                        """)));
    }

    @Test
    void computeRoute_threeWaypoints_sendsMiddleWaypointAsIntermediate(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlPathEqualTo(COMPUTE_ROUTES_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"routes":[]}
                                """)));

        GoogleRoutesClient client =
                new GoogleRoutesClient(computeRoutesRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        client.computeRoute(List.of(
                RouteWaypoint.ofLocation(ORIGIN),
                RouteWaypoint.ofLocation(INTERMEDIATE),
                RouteWaypoint.ofLocation(DESTINATION)));

        verify(postRequestedFor(urlPathEqualTo(COMPUTE_ROUTES_PATH)).withRequestBody(equalToJson("""
                        {
                          "origin": {"location": {"latLng": {"latitude": 33.393, "longitude": -86.930}}},
                          "destination": {"location": {"latLng": {"latitude": 32.735, "longitude": -97.108}}},
                          "intermediates": [{"location": {"latLng": {"latitude": 34.0, "longitude": -111.0}}}],
                          "travelMode": "DRIVE"
                        }
                        """)));
    }

    @Test
    void computeRoute_addressWaypoint_sendsAddressInsteadOfLocation(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlPathEqualTo(COMPUTE_ROUTES_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"routes":[{"distanceMeters":127923,"duration":"5019s","polyline":{"encodedPolyline":"abc123"}}]}
                                """)));

        GoogleRoutesClient client =
                new GoogleRoutesClient(computeRoutesRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        Optional<ComputedRoute> result = client.computeRoute(
                List.of(RouteWaypoint.ofLocation(ORIGIN), RouteWaypoint.ofAddress("Fort Hunter Liggett, CA")));

        assertThat(result).isPresent();

        verify(postRequestedFor(urlPathEqualTo(COMPUTE_ROUTES_PATH)).withRequestBody(equalToJson("""
                        {
                          "origin": {"location": {"latLng": {"latitude": 33.393, "longitude": -86.930}}},
                          "destination": {"address": "Fort Hunter Liggett, CA"},
                          "intermediates": [],
                          "travelMode": "DRIVE"
                        }
                        """)));
    }

    @Test
    void computeRoute_fewerThanTwoWaypoints_throws(WireMockRuntimeInfo wireMockRuntimeInfo) {
        GoogleRoutesClient client =
                new GoogleRoutesClient(computeRoutesRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        assertThatThrownBy(() -> client.computeRoute(List.of(RouteWaypoint.ofLocation(ORIGIN))))
                .isInstanceOf(IllegalArgumentException.class);
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
                client.computeRoute(List.of(RouteWaypoint.ofLocation(ORIGIN), RouteWaypoint.ofLocation(DESTINATION)));

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
                client.computeRoute(List.of(RouteWaypoint.ofLocation(ORIGIN), RouteWaypoint.ofLocation(DESTINATION)));

        assertThat(result).isEmpty();
        verify(2, postRequestedFor(urlPathEqualTo(COMPUTE_ROUTES_PATH)));
    }

    private RestClient computeRoutesRestClient(WireMockRuntimeInfo wireMockRuntimeInfo) {
        return RestClient.builder()
                .baseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
                .build();
    }
}
