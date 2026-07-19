package com.lava.swexpedited.batch.vektor;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.lava.swexpedited.vektor.VektorEtaSnapshotRow;
import com.lava.swexpedited.vektor.VektorGrpcWeb;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@WireMockTest
class VektorTruckEtaStatesClientTest {

    private static final String ETA_STATES_PATH = "/carrier/dashboard/core/envoy/Manifests/TruckEtaStatesGet";

    @Test
    void fetch_parsesEtaSnapshots(WireMockRuntimeInfo wireMockRuntimeInfo) {
        // Field values mirror a real captured snapshot for manifest #1000588's Seguin, TX stop - remainingMiles/
        // remainingMinutes/estimatedArrival here match that manifest's real dispatch-sheet values exactly (Left 553
        // mi / 9h, ETA Jul 19 2:16) - see the Vektor endpoints investigation this client was built from.
        VektorGrpcWeb.Writer targetStop = new VektorGrpcWeb.Writer()
                .writeString(1, "08135acc-8c84-4444-80ce-d18d03f04eae")
                .writeString(2, "78c13acf-de43-4658-b3d4-efbf34a57b5c");
        VektorGrpcWeb.Writer snapshot = new VektorGrpcWeb.Writer()
                .writeString(1, "0e2bb5f0-d639-4ce4-b9bd-73c5ec34a20c")
                .writeMessage(2, targetStop)
                .writeVarint(5, 5)
                .writeDouble(9, 30.4183333d)
                .writeDouble(10, -89.1889962d)
                .writeString(11, "2026-07-19 02:16:00")
                .writeVarint(13, 567)
                .writeDouble(15, 552.86d);
        VektorGrpcWeb.Writer response = new VektorGrpcWeb.Writer().writeMessage(1, snapshot);
        stubFor(post(urlEqualTo(ETA_STATES_PATH))
                .willReturn(aResponse().withStatus(200).withBody(VektorGrpcWeb.encodeUnaryResponse(response))));

        VektorTruckEtaStatesClient client =
                new VektorTruckEtaStatesClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<VektorEtaSnapshotRow> rows =
                client.fetch("test-jwt", "test-company-id", "0e2bb5f0-d639-4ce4-b9bd-73c5ec34a20c");

        assertThat(rows).hasSize(1);
        VektorEtaSnapshotRow row = rows.getFirst();
        assertThat(row.targetStopId()).isEqualTo("78c13acf-de43-4658-b3d4-efbf34a57b5c");
        assertThat(row.targetSequenceNumber()).isEqualTo(5);
        assertThat(row.truckLatitude()).isEqualByComparingTo(BigDecimal.valueOf(30.4183333d));
        assertThat(row.truckLongitude()).isEqualByComparingTo(BigDecimal.valueOf(-89.1889962d));
        assertThat(row.remainingMiles()).isEqualByComparingTo(BigDecimal.valueOf(552.86d));
        assertThat(row.remainingMinutes()).isEqualTo(567);
        assertThat(row.estimatedArrival()).isEqualTo(LocalDateTime.of(2026, 7, 19, 2, 16, 0));
    }

    @Test
    void fetch_sendsTheManifestIdAsTheOnlyRequestField(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlEqualTo(ETA_STATES_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer()))));
        VektorTruckEtaStatesClient client =
                new VektorTruckEtaStatesClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        client.fetch("test-jwt", "test-company-id", "manifest-uuid-1");

        byte[] expectedBody =
                VektorGrpcWeb.encodeUnaryRequest(new VektorGrpcWeb.Writer().writeString(1, "manifest-uuid-1"));
        verify(postRequestedFor(urlEqualTo(ETA_STATES_PATH)).withRequestBody(binaryEqualTo(expectedBody)));
    }

    private RestClient vektorRestClient(WireMockRuntimeInfo wireMockRuntimeInfo) {
        return RestClient.builder()
                .baseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
                .build();
    }
}
