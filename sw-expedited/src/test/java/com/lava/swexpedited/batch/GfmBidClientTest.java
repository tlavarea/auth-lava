package com.lava.swexpedited.batch;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.lava.swexpedited.boot.autoconfigure.app.GfmProperties;
import com.lava.swexpedited.shipment.ShipmentDetailRow;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@WireMockTest
class GfmBidClientTest {

    private static final String FULL_BID_RESPONSE = """
            {
              "bid": {
                "totalAmount": 1416,
                "lineHaulCost": 1200,
                "rateUsed": 2.45,
                "scac": "SWJJ",
                "scacName": "Southwest Expedited Transportation LLC",
                "tenderNumber": "000225",
                "equipmentDesc": "FLAT BED, 30 FT AND LESS",
                "equipment": {
                  "shipment": {
                    "requestorName": "SOPHIA REYESCHUELA",
                    "requestorEmail": "SOPHIA.REYES_CHUELA@US.AF.MIL"
                  }
                }
              }
            }
            """;

    @Test
    void fetchDetail_parsesTypedFieldsAndKeepsRawResponse(WireMockRuntimeInfo wireMockRuntimeInfo) {
        String baseUrl = wireMockRuntimeInfo.getHttpBaseUrl();
        stubFor(get(urlPathEqualTo("/atr/getBid"))
                .withQueryParam("id", equalTo("1317218067"))
                .withQueryParam("readOnly", equalTo("true"))
                .withHeader("Referer", equalTo(baseUrl + "/atr/shipment"))
                .withHeader("Sec-Fetch-Dest", equalTo("empty"))
                .withHeader("Sec-Fetch-Mode", equalTo("cors"))
                .willReturn(aResponse().withStatus(200).withBody(FULL_BID_RESPONSE)));

        GfmBidClient gfmBidClient =
                new GfmBidClient(RestClient.create(), gfmProperties(baseUrl), Duration.ofMillis(10));

        ShipmentDetailRow detail = gfmBidClient.fetchDetail(1317218067L);

        assertThat(detail.offerId()).isEqualTo(1317218067L);
        assertThat(detail.totalAmount()).isEqualByComparingTo(new BigDecimal("1416"));
        assertThat(detail.lineHaulCost()).isEqualByComparingTo(new BigDecimal("1200"));
        assertThat(detail.rateUsed()).isEqualByComparingTo(new BigDecimal("2.45"));
        assertThat(detail.scac()).isEqualTo("SWJJ");
        assertThat(detail.scacName()).isEqualTo("Southwest Expedited Transportation LLC");
        assertThat(detail.tenderNumber()).isEqualTo("000225");
        assertThat(detail.equipmentDesc()).isEqualTo("FLAT BED, 30 FT AND LESS");
        assertThat(detail.requestorName()).isEqualTo("SOPHIA REYESCHUELA");
        assertThat(detail.requestorEmail()).isEqualTo("SOPHIA.REYES_CHUELA@US.AF.MIL");
        assertThat(detail.rawResponse()).isEqualTo(FULL_BID_RESPONSE);
        assertThat(detail.syncedAt()).isNull();
    }

    @Test
    void fetchDetail_missingOptionalFields_returnsNullsRatherThanThrowing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        String baseUrl = wireMockRuntimeInfo.getHttpBaseUrl();
        stubFor(get(urlPathEqualTo("/atr/getBid"))
                .willReturn(aResponse().withStatus(200).withBody("{\"bid\": {}}")));

        GfmBidClient gfmBidClient =
                new GfmBidClient(RestClient.create(), gfmProperties(baseUrl), Duration.ofMillis(10));

        ShipmentDetailRow detail = gfmBidClient.fetchDetail(42L);

        assertThat(detail.totalAmount()).isNull();
        assertThat(detail.scac()).isNull();
        assertThat(detail.requestorName()).isNull();
        assertThat(detail.requestorEmail()).isNull();
    }

    @Test
    void fetchDetail_retriesOn5xxThenSucceeds(WireMockRuntimeInfo wireMockRuntimeInfo) {
        String baseUrl = wireMockRuntimeInfo.getHttpBaseUrl();
        stubFor(get(urlPathEqualTo("/atr/getBid"))
                .inScenario("getBid-retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        stubFor(get(urlPathEqualTo("/atr/getBid"))
                .inScenario("getBid-retry")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200).withBody(FULL_BID_RESPONSE)));

        GfmBidClient gfmBidClient =
                new GfmBidClient(RestClient.create(), gfmProperties(baseUrl), Duration.ofMillis(10));

        ShipmentDetailRow detail = gfmBidClient.fetchDetail(1317218067L);

        assertThat(detail.scac()).isEqualTo("SWJJ");
        verify(2, com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlPathEqualTo("/atr/getBid")));
    }

    private GfmProperties gfmProperties(String baseUrl) {
        return new GfmProperties(null, null, null, null, "test-client", baseUrl, baseUrl, baseUrl);
    }
}
