package com.lava.swexpedited.batch.gfm;

import com.lava.swexpedited.batch.RetryingHttpClient;
import com.lava.swexpedited.boot.autoconfigure.app.GfmProperties;
import com.lava.swexpedited.configuration.GfmFetchMetadataHeaders;
import com.lava.swexpedited.logging.LogSanitizer;
import com.lava.swexpedited.repository.ShipmentListingRepository;
import com.lava.swexpedited.shipment.ShipmentListingRow;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.jspecify.annotations.Nullable;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Logs into the DoD GFM/ATR shipment system via the IdentTrust client cert configured on {@code gfmRestClient} and
 * downloads the current-shipments CSV, entirely in memory (no intermediate file - a local-disk artifact doesn't survive
 * across ephemeral/multi-instance deployments the way a single held HttpClient's cookie store does for the lifetime of
 * one execution). Ported from the working chain in scjj-gfm-app's FetchCurrentShipmentsTasklet: same hosts, paths, and
 * query params, since those are the parts that were already validated against the live system - except two gaps a real
 * browser HAR capture caught that the ported chain missed: mps-kmis.transport.mil/oauth2/authorize is only the start of
 * GFM's ECA-PKI SAML/mTLS federation handshake, not a complete login by itself (see {@link GfmEcaPkiAuthenticator}),
 * and {@code POST /atr/home} is a real screen-navigation form submit (GFM's gateway portal's "ATR" link posts
 * {@code FROMSCREENNAME}/{@code TOSCREENNAME}, not an empty body) whose absence caused the server's JSP/SiteMesh view
 * rendering to 500 even after authentication succeeded.
 */
@Component
@Slf4j
public class FetchAndLoadShipmentsTasklet extends RetryingHttpClient implements Tasklet {

    private final RestClient gfmRestClient;
    private final CookieStore gfmCookieStore;
    private final GfmProperties gfmProperties;
    private final GfmEcaPkiAuthenticator gfmEcaPkiAuthenticator;
    private final AtomicReference<String> gfmGatewayReferer;
    private final ShipmentCsvParser shipmentCsvParser;
    private final ShipmentListingRepository shipmentListingRepository;
    private final Duration retryBackoff;

    public FetchAndLoadShipmentsTasklet(
            @Qualifier("gfmRestClient") RestClient gfmRestClient,
            @Qualifier("gfmCookieStore") CookieStore gfmCookieStore,
            GfmProperties gfmProperties,
            GfmEcaPkiAuthenticator gfmEcaPkiAuthenticator,
            @Qualifier("gfmGatewayReferer") AtomicReference<String> gfmGatewayReferer,
            ShipmentCsvParser shipmentCsvParser,
            ShipmentListingRepository shipmentListingRepository,
            @Value("${gfm.retry-backoff:5s}") Duration retryBackoff) {
        this.gfmRestClient = gfmRestClient;
        this.gfmCookieStore = gfmCookieStore;
        this.gfmProperties = gfmProperties;
        this.gfmEcaPkiAuthenticator = gfmEcaPkiAuthenticator;
        this.gfmGatewayReferer = gfmGatewayReferer;
        this.shipmentCsvParser = shipmentCsvParser;
        this.shipmentListingRepository = shipmentListingRepository;
        this.retryBackoff = retryBackoff;
    }

    @Override
    public @Nullable RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        URI teamsLoginUri = UriComponentsBuilder.fromUriString(this.gfmProperties.etaTeamsBaseUrl())
                .path("/teams/api/oauth/login")
                .queryParam("loginType", "certificate")
                .build()
                .toUri();
        retrying(() -> this.gfmRestClient.get().uri(teamsLoginUri).retrieve().toBodilessEntity(), this.retryBackoff);
        log.info("execute::teams login successful");
        logCookieNames("after teams login");

        URI gfmAuthorizeUri = UriComponentsBuilder.fromUriString(this.gfmProperties.mpsKmisBaseUrl())
                .path("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("scope", "openid")
                .queryParam("client_id", this.gfmProperties.clientId())
                .queryParam("redirect_uri", this.gfmProperties.gfmBaseUrl() + "/gfmgateway/GfmGateway")
                .build()
                .toUri();
        this.gfmEcaPkiAuthenticator.authenticate(gfmAuthorizeUri);
        log.info("execute::gfm login successful");
        logCookieNames("after gfm login");

        URI atrHomeUri = UriComponentsBuilder.fromUriString(this.gfmProperties.gfmBaseUrl())
                .path("/atr/home")
                .build()
                .toUri();
        MultiValueMap<String, String> atrHomeForm = new LinkedMultiValueMap<>();
        atrHomeForm.add("FROMSCREENNAME", "MAINMENUSCREEN");
        atrHomeForm.add("TOSCREENNAME", "MAINMENUSCREEN");
        // A real browser's Referer here is the exact /gfmgateway/GfmGateway?code=...&session_state=... URL it landed
        // on after login - not just the bare base URL + path this used to send. gfmGatewayReferer is populated by
        // GfmHttpConfiguration's request interceptor as the login chain runs; the static fallback only applies if
        // that hop's shape ever changes and the interceptor stops matching it.
        String atrHomeReferer = this.gfmGatewayReferer.get() != null
                ? this.gfmGatewayReferer.get()
                : this.gfmProperties.gfmBaseUrl() + "/gfmgateway/GfmGateway";
        log.info("execute::atrHomeReferer: {}", LogSanitizer.sanitize(atrHomeReferer));
        // Same cookies, Referer, Origin, User-Agent, and body as a real browser still 500'd - a curl replay of the
        // exact successful browser request isolated it to Fetch Metadata / Client Hint headers specifically (see
        // GfmFetchMetadataHeaders). /atr/home and /atr/shipment are both real page navigations (GFM's own
        // screen-navigation forms/links, not XHR calls) - /atr/getBid, used elsewhere for per-shipment bid detail,
        // is an in-page XHR and uses GfmFetchMetadataHeaders.forXhr instead.
        retrying(
                () -> GfmFetchMetadataHeaders.forDocumentNavigation(this.gfmRestClient
                                .post()
                                .uri(atrHomeUri)
                                .header(HttpHeaders.REFERER, atrHomeReferer)
                                .header(HttpHeaders.ORIGIN, this.gfmProperties.gfmBaseUrl())
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .body(atrHomeForm))
                        .retrieve()
                        .toBodilessEntity(),
                this.retryBackoff);
        log.info("execute::atr login successful");

        URI shipmentCsvUri = UriComponentsBuilder.fromUriString(this.gfmProperties.gfmBaseUrl())
                .path("/atr/shipment")
                .queryParam("d-3693239-e", "1")
                .queryParam("6578706f7274", "1")
                .queryParam("allintdom", "D")
                .build()
                .toUri();
        byte[] csv = retrying(
                () -> GfmFetchMetadataHeaders.forDocumentNavigation(this.gfmRestClient
                                .get()
                                .uri(shipmentCsvUri)
                                .header(HttpHeaders.REFERER, this.gfmProperties.gfmBaseUrl() + "/atr/shipment"))
                        .retrieve()
                        .body(byte[].class),
                this.retryBackoff);
        log.info("execute::current shipments downloaded successfully");

        List<ShipmentListingRow> rows = this.shipmentCsvParser.parse(csv);
        this.shipmentListingRepository.replaceAll(rows);
        log.info("execute::stored {} shipment listings", rows.size());

        return RepeatStatus.FINISHED;
    }

    /**
     * Diagnostic only (see the login-chain debugging that motivated {@link GfmEcaPkiAuthenticator} and the
     * {@code /atr/home} form-body fix) - logs cookie names and domains, never values, so this is safe to leave at debug
     * level without leaking session tokens into logs.
     */
    private void logCookieNames(String checkpoint) {
        String cookies = this.gfmCookieStore.getCookies().stream()
                .map(cookie -> cookie.getName() + "@" + cookie.getDomain())
                .collect(Collectors.joining(", "));
        log.debug("execute::cookies present {}: {}", checkpoint, LogSanitizer.sanitize(cookies));
    }
}
