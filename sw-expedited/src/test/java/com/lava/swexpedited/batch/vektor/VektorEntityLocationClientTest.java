package com.lava.swexpedited.batch.vektor;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.lava.swexpedited.vektor.VektorDriverLocationRow;
import com.lava.swexpedited.vektor.VektorGrpcWeb;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@WireMockTest
class VektorEntityLocationClientTest {

    private static final String GET_ALL_PATH = "/carrier/dashboard/core/envoy/EntityLocation/GetAll";

    @Test
    void fetchAll_parsesDriverLocationsFromFieldThree(WireMockRuntimeInfo wireMockRuntimeInfo) {
        VektorGrpcWeb.Writer driverOne = new VektorGrpcWeb.Writer()
                .writeString(1, "driver-uuid-1")
                .writeString(2, "ping-uuid-1")
                .writeDouble(3, 30.4183333d)
                .writeDouble(4, -89.1889962d)
                .writeDouble(5, 294.91d)
                .writeVarint(6, 1784427487135L)
                .writeString(7, "Long Beach, MS");
        VektorGrpcWeb.Writer response = new VektorGrpcWeb.Writer().writeMessage(3, driverOne);
        stubFor(post(urlEqualTo(GET_ALL_PATH))
                .withHeader("Authorization", equalTo("Bearer test-jwt"))
                .withHeader("company_id", equalTo("test-company-id"))
                .willReturn(aResponse().withStatus(200).withBody(VektorGrpcWeb.encodeUnaryResponse(response))));

        VektorEntityLocationClient client =
                new VektorEntityLocationClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<VektorDriverLocationRow> rows = client.fetchAll("test-jwt", "test-company-id");

        assertThat(rows).hasSize(1);
        VektorDriverLocationRow row = rows.getFirst();
        assertThat(row.driverId()).isEqualTo("driver-uuid-1");
        assertThat(row.latitude()).isEqualByComparingTo(BigDecimal.valueOf(30.4183333d));
        assertThat(row.longitude()).isEqualByComparingTo(BigDecimal.valueOf(-89.1889962d));
        assertThat(row.headingDegrees()).isEqualByComparingTo(BigDecimal.valueOf(294.91d));
        assertThat(row.asOf()).isEqualTo(LocalDateTime.of(2026, 7, 19, 2, 18, 7, 135_000_000));
        assertThat(row.formattedLocation()).isEqualTo("Long Beach, MS");
    }

    @Test
    void fetchAll_entryMissingOptionalFields_stillParsesRequiredOnes(WireMockRuntimeInfo wireMockRuntimeInfo) {
        VektorGrpcWeb.Writer driverWithoutHeadingOrAddress = new VektorGrpcWeb.Writer()
                .writeString(1, "driver-uuid-2")
                .writeDouble(3, 34.557077d)
                .writeDouble(4, -112.403246d)
                .writeVarint(6, 1765922100000L);
        VektorGrpcWeb.Writer response = new VektorGrpcWeb.Writer().writeMessage(3, driverWithoutHeadingOrAddress);
        stubFor(post(urlEqualTo(GET_ALL_PATH))
                .willReturn(aResponse().withStatus(200).withBody(VektorGrpcWeb.encodeUnaryResponse(response))));

        VektorEntityLocationClient client =
                new VektorEntityLocationClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<VektorDriverLocationRow> rows = client.fetchAll("test-jwt", "test-company-id");

        assertThat(rows).hasSize(1);
        VektorDriverLocationRow row = rows.getFirst();
        assertThat(row.driverId()).isEqualTo("driver-uuid-2");
        assertThat(row.headingDegrees()).isNull();
        assertThat(row.formattedLocation()).isNull();
    }

    @Test
    void fetchAll_sendsAGenuinelyEmptyRequestBody(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlEqualTo(GET_ALL_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer()))));
        VektorEntityLocationClient client =
                new VektorEntityLocationClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        client.fetchAll("test-jwt", "test-company-id");

        byte[] expectedBody = VektorGrpcWeb.encodeUnaryRequest(new VektorGrpcWeb.Writer());
        verify(postRequestedFor(urlEqualTo(GET_ALL_PATH))
                .withHeader("company_id", equalTo("test-company-id"))
                .withRequestBody(binaryEqualTo(expectedBody)));
    }

    private RestClient vektorRestClient(WireMockRuntimeInfo wireMockRuntimeInfo) {
        return RestClient.builder()
                .baseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
                .build();
    }
}
