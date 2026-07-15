package com.lava.swexpedited.batch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lava.swexpedited.boot.autoconfigure.app.GfmProperties;
import com.lava.swexpedited.configuration.GfmFetchMetadataHeaders;
import com.lava.swexpedited.shipment.ShipmentDetailRow;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Fetches the GFM/ATR "getBid" response for one shipment offer, over the same authenticated session
 * {@code FetchAndLoadShipmentsTasklet}'s login chain already established for this job run - no separate login happens
 * here. Only a handful of fields the UI is likely to filter/sort/display on are pulled out of the response via
 * {@link JsonNode} path navigation; everything else is kept as the raw response body rather than modeled into Java
 * classes, since the payload is deeply nested and owned by a system outside our control (see
 * 002-create-shipment-detail.yaml). Path navigation also degrades gracefully if GFM adds/renames fields, unlike strict
 * deserialization into a matching class tree.
 *
 * <p>Builds its own {@link ObjectMapper} rather than injecting Spring's auto-configured one - Spring Boot 4.1's own
 * Jackson auto-configuration is for Jackson 3 ({@code tools.jackson.databind}), so no bean of this (Jackson 2,
 * {@code com.fasterxml.jackson.databind}) type is registered even though the class is on the classpath (pulled in
 * transitively via jackson-dataformat-csv). Same reasoning as {@code ShipmentCsvParser}'s own {@code CsvMapper} field.
 */
@Component
public class GfmBidClient {

    private final RestClient gfmRestClient;
    private final GfmProperties gfmProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Duration retryBackoff;

    public GfmBidClient(
            @Qualifier("gfmRestClient") RestClient gfmRestClient,
            GfmProperties gfmProperties,
            @Value("${gfm.retry-backoff:5s}") Duration retryBackoff) {
        this.gfmRestClient = gfmRestClient;
        this.gfmProperties = gfmProperties;
        this.retryBackoff = retryBackoff;
    }

    public ShipmentDetailRow fetchDetail(long offerId) {
        URI getBidUri = UriComponentsBuilder.fromUriString(gfmProperties.gfmBaseUrl())
                .path("/atr/getBid")
                .queryParam("id", offerId)
                .queryParam("readOnly", true)
                .queryParam("requestTime", Instant.now().toEpochMilli())
                .build()
                .toUri();

        // In-page XHR fired while the browser is still on /atr/shipment (that's the page listing shipments to click
        // into), not a fresh navigation - see GfmFetchMetadataHeaders for why the header shape differs from
        // FetchAndLoadShipmentsTasklet's navigation calls.
        String rawResponse = retrying(() -> GfmFetchMetadataHeaders.forXhr(gfmRestClient
                        .get()
                        .uri(getBidUri)
                        .header(HttpHeaders.REFERER, gfmProperties.gfmBaseUrl() + "/atr/shipment"))
                .retrieve()
                .body(String.class));

        JsonNode root;
        try {
            root = objectMapper.readTree(rawResponse);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to parse getBid response for offer " + offerId, e);
        }
        JsonNode bid = root.path("bid");
        JsonNode shipment = bid.path("equipment").path("shipment");

        return new ShipmentDetailRow(
                offerId,
                decimal(bid, "totalAmount"),
                decimal(bid, "lineHaulCost"),
                decimal(bid, "rateUsed"),
                text(bid, "scac"),
                text(bid, "scacName"),
                text(bid, "tenderNumber"),
                text(bid, "equipmentDesc"),
                text(shipment, "requestorName"),
                text(shipment, "requestorEmail"),
                rawResponse,
                null);
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.decimalValue();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private <T> T retrying(Supplier<T> call) {
        RetryTemplate retryTemplate = RetryTemplate.builder()
                .maxAttempts(4)
                .fixedBackoff(retryBackoff)
                .retryOn(HttpServerErrorException.class)
                .build();
        return retryTemplate.execute(context -> call.get());
    }
}
