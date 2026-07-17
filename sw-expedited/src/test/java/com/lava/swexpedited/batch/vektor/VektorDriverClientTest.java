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
import com.lava.swexpedited.vektor.VektorGrpcWeb;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@WireMockTest
class VektorDriverClientTest {

    private static final String DRIVERS_GET_PATH = "/carrier/dashboard/fleet/envoy/Drivers/Get";

    @Test
    void fetchDriverNamesById_parsesIdToFullNameMap(WireMockRuntimeInfo wireMockRuntimeInfo) {
        // Field values here are synthetic test data, not the real driver PII this endpoint returns in production -
        // see the Vektor manifest sync plan's note on never committing real captured Drivers/Get responses.
        VektorGrpcWeb.Writer driverOne = new VektorGrpcWeb.Writer()
                .writeString(1, "driver-uuid-1")
                .writeString(4, "Test")
                .writeString(6, "Driver")
                .writeString(35, "Test Driver");
        VektorGrpcWeb.Writer driverTwo =
                new VektorGrpcWeb.Writer().writeString(1, "driver-uuid-2").writeString(35, "Another Driver");
        VektorGrpcWeb.Writer response =
                new VektorGrpcWeb.Writer().writeMessage(1, driverOne).writeMessage(1, driverTwo);
        stubFor(post(urlEqualTo(DRIVERS_GET_PATH))
                .withHeader("Authorization", equalTo("Bearer test-jwt"))
                .withHeader("company_id", equalTo("test-company-id"))
                .willReturn(aResponse().withStatus(200).withBody(VektorGrpcWeb.encodeUnaryResponse(response))));

        VektorDriverClient client =
                new VektorDriverClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        Map<String, String> namesById = client.fetchDriverNamesById("test-jwt", "test-company-id");

        assertThat(namesById)
                .containsEntry("driver-uuid-1", "Test Driver")
                .containsEntry("driver-uuid-2", "Another Driver");
    }

    @Test
    void fetchDriverNamesById_driverWithoutFullNameField_isOmittedFromMap(WireMockRuntimeInfo wireMockRuntimeInfo) {
        VektorGrpcWeb.Writer driverWithoutName = new VektorGrpcWeb.Writer().writeString(1, "driver-uuid-1");
        VektorGrpcWeb.Writer response = new VektorGrpcWeb.Writer().writeMessage(1, driverWithoutName);
        stubFor(post(urlEqualTo(DRIVERS_GET_PATH))
                .willReturn(aResponse().withStatus(200).withBody(VektorGrpcWeb.encodeUnaryResponse(response))));

        VektorDriverClient client =
                new VektorDriverClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        Map<String, String> namesById = client.fetchDriverNamesById("test-jwt", "test-company-id");

        assertThat(namesById).isEmpty();
    }

    @Test
    void fetchDriverNamesById_sendsTheConfirmedRequestBody(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlEqualTo(DRIVERS_GET_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer()))));
        VektorDriverClient client =
                new VektorDriverClient(vektorRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        client.fetchDriverNamesById("test-jwt", "test-company-id");

        byte[] expectedBody = VektorGrpcWeb.encodeUnaryRequest(new VektorGrpcWeb.Writer().writeVarint(1, 1));
        verify(postRequestedFor(urlEqualTo(DRIVERS_GET_PATH))
                .withHeader("company_id", equalTo("test-company-id"))
                .withRequestBody(binaryEqualTo(expectedBody)));
    }

    private RestClient vektorRestClient(WireMockRuntimeInfo wireMockRuntimeInfo) {
        return RestClient.builder()
                .baseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
                .build();
    }
}
