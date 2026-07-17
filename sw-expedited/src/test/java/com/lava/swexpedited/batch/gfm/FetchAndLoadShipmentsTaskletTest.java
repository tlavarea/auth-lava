package com.lava.swexpedited.batch.gfm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.lava.swexpedited.boot.autoconfigure.app.GfmProperties;
import com.lava.swexpedited.repository.ShipmentListingRepository;
import com.lava.swexpedited.shipment.ShipmentListingRow;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@ExtendWith(MockitoExtension.class)
@WireMockTest
class FetchAndLoadShipmentsTaskletTest {

    @Mock
    private GfmEcaPkiAuthenticator gfmEcaPkiAuthenticator;

    @Mock
    private ShipmentCsvParser shipmentCsvParser;

    @Mock
    private ShipmentListingRepository shipmentListingRepository;

    @Test
    void execute_runsAuthChainThenParsesAndStores(WireMockRuntimeInfo wireMockRuntimeInfo) {
        String baseUrl = wireMockRuntimeInfo.getHttpBaseUrl();
        byte[] csv = "Offer,Status\n1,Open\n".getBytes(StandardCharsets.UTF_8);
        List<ShipmentListingRow> parsedRows = List.of(new ShipmentListingRow(
                1L,
                "Open",
                null,
                "SHIP1",
                "FAK",
                "1",
                "GBLOC",
                "origin",
                "destination",
                "AF2",
                1,
                0,
                null,
                null,
                null));
        when(shipmentCsvParser.parse(eq(csv))).thenReturn(parsedRows);

        stubFor(get(urlPathEqualTo("/teams/api/oauth/login"))
                .withQueryParam("loginType", equalTo("certificate"))
                .willReturn(aResponse().withStatus(200)));
        stubFor(post(urlPathEqualTo("/atr/home")).willReturn(aResponse().withStatus(200)));
        stubFor(get(urlPathEqualTo("/atr/shipment"))
                .withQueryParam("d-3693239-e", equalTo("1"))
                .withQueryParam("6578706f7274", equalTo("1"))
                .withQueryParam("allintdom", equalTo("D"))
                .withHeader("Referer", equalTo(baseUrl + "/atr/shipment"))
                .withHeader("Sec-Fetch-Dest", equalTo("document"))
                .willReturn(aResponse().withStatus(200).withBody(csv)));

        String gfmGatewayLandingUrl = baseUrl + "/gfmgateway/GfmGateway?code=test-code&session_state=test-session";
        FetchAndLoadShipmentsTasklet tasklet = new FetchAndLoadShipmentsTasklet(
                RestClient.create(),
                new BasicCookieStore(),
                gfmProperties(baseUrl),
                gfmEcaPkiAuthenticator,
                new AtomicReference<>(gfmGatewayLandingUrl),
                shipmentCsvParser,
                shipmentListingRepository,
                Duration.ofMillis(10));

        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        ArgumentCaptor<URI> authorizeUri = ArgumentCaptor.forClass(URI.class);
        org.mockito.Mockito.verify(gfmEcaPkiAuthenticator).authenticate(authorizeUri.capture());
        assertThat(authorizeUri.getValue())
                .isEqualTo(UriComponentsBuilder.fromUriString(baseUrl)
                        .path("/oauth2/authorize")
                        .queryParam("response_type", "code")
                        .queryParam("scope", "openid")
                        .queryParam("client_id", "test-client")
                        .queryParam("redirect_uri", baseUrl + "/gfmgateway/GfmGateway")
                        .build()
                        .toUri());
        org.mockito.Mockito.verify(shipmentListingRepository).replaceAll(parsedRows);
        verify(1, getRequestedFor(urlPathEqualTo("/atr/shipment")));
        verify(
                1,
                postRequestedFor(urlPathEqualTo("/atr/home"))
                        .withHeader("Referer", equalTo(gfmGatewayLandingUrl))
                        .withHeader("Origin", equalTo(baseUrl))
                        .withHeader("Sec-Fetch-Dest", equalTo("document"))
                        .withHeader("Sec-Fetch-Mode", equalTo("navigate"))
                        .withHeader("Sec-Fetch-Site", equalTo("same-origin"))
                        .withHeader("Sec-Fetch-User", equalTo("?1"))
                        .withHeader("Upgrade-Insecure-Requests", equalTo("1"))
                        .withRequestBody(containing("FROMSCREENNAME=MAINMENUSCREEN"))
                        .withRequestBody(containing("TOSCREENNAME=MAINMENUSCREEN")));
    }

    @Test
    void execute_retriesOn5xxThenSucceeds(WireMockRuntimeInfo wireMockRuntimeInfo) {
        String baseUrl = wireMockRuntimeInfo.getHttpBaseUrl();
        byte[] csv = "Offer,Status\n1,Open\n".getBytes(StandardCharsets.UTF_8);
        when(shipmentCsvParser.parse(eq(csv))).thenReturn(List.of());

        stubFor(get(urlPathEqualTo("/teams/api/oauth/login"))
                .willReturn(aResponse().withStatus(200)));
        stubFor(post(urlPathEqualTo("/atr/home"))
                .inScenario("atr-home-retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        stubFor(post(urlPathEqualTo("/atr/home"))
                .inScenario("atr-home-retry")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200)));
        stubFor(get(urlPathEqualTo("/atr/shipment"))
                .willReturn(aResponse().withStatus(200).withBody(csv)));

        FetchAndLoadShipmentsTasklet tasklet = new FetchAndLoadShipmentsTasklet(
                RestClient.create(),
                new BasicCookieStore(),
                gfmProperties(baseUrl),
                gfmEcaPkiAuthenticator,
                new AtomicReference<>(),
                shipmentCsvParser,
                shipmentListingRepository,
                Duration.ofMillis(10));

        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(2, postRequestedFor(urlPathEqualTo("/atr/home")));
    }

    private GfmProperties gfmProperties(String baseUrl) {
        return new GfmProperties(null, null, null, null, "test-client", baseUrl, baseUrl, baseUrl);
    }
}
