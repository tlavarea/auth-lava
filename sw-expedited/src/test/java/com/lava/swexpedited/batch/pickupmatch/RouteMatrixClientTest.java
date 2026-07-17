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
import com.lava.swexpedited.batch.pickupmatch.RouteMatrixClient.LatLng;
import com.lava.swexpedited.batch.pickupmatch.RouteMatrixClient.RouteMatrixElement;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@WireMockTest
class RouteMatrixClientTest {

    private static final String COMPUTE_ROUTE_MATRIX_PATH = "/distanceMatrix/v2:computeRouteMatrix";

    @Test
    void computeRouteMatrix_singlePair_sendsAddressAndLocationWaypointsAndParsesResponse(
            WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlPathEqualTo(COMPUTE_ROUTE_MATRIX_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [{"originIndex":0,"destinationIndex":0,"distanceMeters":160934,"condition":"ROUTE_EXISTS","duration":"7203.500s","status":{}}]
                                """)));

        RouteMatrixClient client =
                new RouteMatrixClient(routeMatrixRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<RouteMatrixElement> elements = client.computeRouteMatrix(
                List.of("Bessemer, AL"), List.of(new LatLng(new BigDecimal("33.101"), new BigDecimal("-87.99"))));

        assertThat(elements).hasSize(1);
        RouteMatrixElement element = elements.getFirst();
        assertThat(element.originIndex()).isEqualTo(0);
        assertThat(element.destinationIndex()).isEqualTo(0);
        assertThat(element.routeExists()).isTrue();
        assertThat(element.distanceMiles()).isEqualByComparingTo(new BigDecimal("100.0"));
        assertThat(element.durationValue()).isEqualTo(Duration.ofSeconds(7203).plusMillis(500));

        verify(postRequestedFor(urlPathEqualTo(COMPUTE_ROUTE_MATRIX_PATH)).withRequestBody(equalToJson("""
                        {
                          "origins": [{"waypoint": {"address": "Bessemer, AL"}}],
                          "destinations": [{"waypoint": {"location": {"latLng": {"latitude": 33.101, "longitude": -87.99}}}}],
                          "travelMode": "DRIVE"
                        }
                        """)));
    }

    @Test
    void computeRouteMatrix_noRouteExists_distanceMilesIsNull(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlPathEqualTo(COMPUTE_ROUTE_MATRIX_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [{"originIndex":0,"destinationIndex":0,"condition":"ROUTE_NOT_FOUND","status":{}}]
                                """)));

        RouteMatrixClient client =
                new RouteMatrixClient(routeMatrixRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<RouteMatrixElement> elements = client.computeRouteMatrix(
                List.of("Nowhere"), List.of(new LatLng(new BigDecimal("0"), new BigDecimal("0"))));

        assertThat(elements).hasSize(1);
        assertThat(elements.getFirst().routeExists()).isFalse();
        assertThat(elements.getFirst().distanceMiles()).isNull();
        assertThat(elements.getFirst().durationValue()).isNull();
    }

    @Test
    void computeRouteMatrix_largeLists_batchesRequestsAndCorrectsIndices(WireMockRuntimeInfo wireMockRuntimeInfo) {
        // 30 origins x 1 destination needs two batched requests (BATCH_SIZE = 25): [0,25) and [25,30).
        stubFor(post(urlPathEqualTo(COMPUTE_ROUTE_MATRIX_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [{"originIndex":0,"destinationIndex":0,"distanceMeters":1000,"condition":"ROUTE_EXISTS"}]
                                """)));

        RouteMatrixClient client =
                new RouteMatrixClient(routeMatrixRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));
        List<String> originAddresses = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            originAddresses.add("Origin " + i);
        }

        List<RouteMatrixElement> elements = client.computeRouteMatrix(
                originAddresses, List.of(new LatLng(new BigDecimal("0"), new BigDecimal("0"))));

        assertThat(elements).hasSize(2);
        assertThat(elements).extracting(RouteMatrixElement::originIndex).containsExactlyInAnyOrder(0, 25);
        verify(2, postRequestedFor(urlPathEqualTo(COMPUTE_ROUTE_MATRIX_PATH)));
    }

    @Test
    void computeRouteMatrix_retriesOn5xxThenSucceeds(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlPathEqualTo(COMPUTE_ROUTE_MATRIX_PATH))
                .inScenario("route-matrix-retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        stubFor(post(urlPathEqualTo(COMPUTE_ROUTE_MATRIX_PATH))
                .inScenario("route-matrix-retry")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        RouteMatrixClient client =
                new RouteMatrixClient(routeMatrixRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<RouteMatrixElement> elements = client.computeRouteMatrix(
                List.of("Bessemer, AL"), List.of(new LatLng(new BigDecimal("0"), new BigDecimal("0"))));

        assertThat(elements).isEmpty();
        verify(2, postRequestedFor(urlPathEqualTo(COMPUTE_ROUTE_MATRIX_PATH)));
    }

    private RestClient routeMatrixRestClient(WireMockRuntimeInfo wireMockRuntimeInfo) {
        return RestClient.builder()
                .baseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
                .build();
    }
}
