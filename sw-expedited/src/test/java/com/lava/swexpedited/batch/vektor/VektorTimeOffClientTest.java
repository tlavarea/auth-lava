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
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@WireMockTest
class VektorTimeOffClientTest {

    private static final String TIME_OFF_GET_PATH = "/carrier/dashboard/fleet/envoy/TruckTimeOff/Get";
    private static final LocalDate FROM_DATE = LocalDate.of(2026, 7, 5);

    @Test
    void fetchTimeOff_flattensEntriesOutOfTruckGroupAndWrapperNesting(WireMockRuntimeInfo wireMockRuntimeInfo) {
        VektorGrpcWeb.Writer entry1 = new VektorGrpcWeb.Writer().writeString(2, "time-off-1");
        VektorGrpcWeb.Writer entry2 = new VektorGrpcWeb.Writer().writeString(2, "time-off-2");
        VektorGrpcWeb.Writer entriesWrapper =
                new VektorGrpcWeb.Writer().writeMessage(1, entry1).writeMessage(1, entry2);
        VektorGrpcWeb.Writer truckGroup = new VektorGrpcWeb.Writer().writeMessage(2, entriesWrapper);
        VektorGrpcWeb.Writer response = new VektorGrpcWeb.Writer().writeMessage(1, truckGroup);
        stubFor(post(urlEqualTo(TIME_OFF_GET_PATH))
                .withHeader("Authorization", equalTo("Bearer test-jwt"))
                .withHeader("company_id", equalTo("test-company-id"))
                .willReturn(aResponse().withStatus(200).withBody(VektorGrpcWeb.encodeUnaryResponse(response))));

        VektorTimeOffClient client =
                new VektorTimeOffClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<VektorGrpcWeb.Message> entries = client.fetchTimeOff("test-jwt", "test-company-id", FROM_DATE);

        assertThat(entries).extracting(e -> e.getString(2).orElseThrow()).containsExactly("time-off-1", "time-off-2");
    }

    @Test
    void fetchTimeOff_multipleTruckGroups_flattensAllOfThem(WireMockRuntimeInfo wireMockRuntimeInfo) {
        VektorGrpcWeb.Writer truckOneEntries =
                new VektorGrpcWeb.Writer().writeMessage(1, new VektorGrpcWeb.Writer().writeString(2, "time-off-1"));
        VektorGrpcWeb.Writer truckOneGroup = new VektorGrpcWeb.Writer().writeMessage(2, truckOneEntries);
        VektorGrpcWeb.Writer truckTwoEntries =
                new VektorGrpcWeb.Writer().writeMessage(1, new VektorGrpcWeb.Writer().writeString(2, "time-off-2"));
        VektorGrpcWeb.Writer truckTwoGroup = new VektorGrpcWeb.Writer().writeMessage(2, truckTwoEntries);
        VektorGrpcWeb.Writer response =
                new VektorGrpcWeb.Writer().writeMessage(1, truckOneGroup).writeMessage(1, truckTwoGroup);
        stubFor(post(urlEqualTo(TIME_OFF_GET_PATH))
                .willReturn(aResponse().withStatus(200).withBody(VektorGrpcWeb.encodeUnaryResponse(response))));

        VektorTimeOffClient client =
                new VektorTimeOffClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<VektorGrpcWeb.Message> entries = client.fetchTimeOff("test-jwt", "test-company-id", FROM_DATE);

        assertThat(entries).extracting(e -> e.getString(2).orElseThrow()).containsExactly("time-off-1", "time-off-2");
    }

    @Test
    void fetchTimeOff_sendsSingleLowerBoundDateInConfirmedFormat(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlEqualTo(TIME_OFF_GET_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer()))));
        VektorTimeOffClient client =
                new VektorTimeOffClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        client.fetchTimeOff("test-jwt", "test-company-id", FROM_DATE);

        byte[] expectedBody =
                VektorGrpcWeb.encodeUnaryRequest(new VektorGrpcWeb.Writer().writeString(1, "2026-07-05 00:00:00"));
        verify(postRequestedFor(urlEqualTo(TIME_OFF_GET_PATH))
                .withHeader("company_id", equalTo("test-company-id"))
                .withRequestBody(binaryEqualTo(expectedBody)));
    }

    @Test
    void fetchTimeOff_emptyResponseBody_throwsWithStatusAndGrpcHeadersRatherThanNpe(
            WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlEqualTo(TIME_OFF_GET_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("grpc-status", "3")
                        .withHeader("grpc-message", "invalid argument")));

        VektorTimeOffClient client =
                new VektorTimeOffClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        assertThatThrownBy(() -> client.fetchTimeOff("test-jwt", "test-company-id", FROM_DATE))
                .isInstanceOf(VektorGrpcWeb.VektorGrpcWebException.class)
                .hasMessageContaining("TruckTimeOff/Get")
                .hasMessageContaining("200")
                .hasMessageContaining("grpc-status=3")
                .hasMessageContaining("grpc-message=invalid argument");
    }

    @Test
    void fetchTimeOff_retriesOn5xxThenSucceeds(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlEqualTo(TIME_OFF_GET_PATH))
                .inScenario("time-off-retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        stubFor(post(urlEqualTo(TIME_OFF_GET_PATH))
                .inScenario("time-off-retry")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer()))));

        VektorTimeOffClient client =
                new VektorTimeOffClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<VektorGrpcWeb.Message> entries = client.fetchTimeOff("test-jwt", "test-company-id", FROM_DATE);

        assertThat(entries).isEmpty();
        verify(2, postRequestedFor(urlPathEqualTo(TIME_OFF_GET_PATH)));
    }

    private RestClient vektorRestClient(WireMockRuntimeInfo wireMockRuntimeInfo) {
        return RestClient.builder()
                .baseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
                .build();
    }
}
