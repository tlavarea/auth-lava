package com.lava.swexpedited.batch.gfm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lava.swexpedited.batch.RetryingHttpClient;
import com.lava.swexpedited.boot.autoconfigure.app.GfmProperties;
import com.lava.swexpedited.configuration.GfmFetchMetadataHeaders;
import com.lava.swexpedited.gfm.model.Bid;
import com.lava.swexpedited.gfm.model.Equipment;
import com.lava.swexpedited.gfm.model.GfmGetBidResponse;
import com.lava.swexpedited.gfm.model.GfmShipment;
import com.lava.swexpedited.shipment.ShipmentDetailRow;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Fetches the GFM/ATR "getBid" response for one shipment offer, over the same authenticated session
 * {@code FetchAndLoadShipmentsTasklet}'s login chain already established for this job run - no separate login happens
 * here. The response is deserialized into the {@code com.lava.swexpedited.gfm.model} classes generated (via
 * {@code jsonschema2pojo-maven-plugin}, bound to {@code generate-sources}) from
 * {@code src/main/resources/schema/gfm-bid-response.schema.json}; only the fields this app actually surfaces are
 * modeled in that schema, and the rest fall into each generated class's {@code additionalProperties} map
 * (jsonschema2pojo's {@code includeAdditionalProperties} defaults to true) rather than failing deserialization if GFM
 * adds/renames fields. {@code rawResponse} is still kept in full alongside the typed fields, since the payload is owned
 * by a system outside our control (see 002-create-shipment-detail.yaml) and nothing the UI's detail view needs should
 * be lost to under-modeling it.
 *
 * <p>Builds its own {@link ObjectMapper} rather than injecting Spring's auto-configured one - Spring Boot 4.1's own
 * Jackson auto-configuration is for Jackson 3 ({@code tools.jackson.databind}), so no bean of this (Jackson 2,
 * {@code com.fasterxml.jackson.databind}) type is registered even though the class is on the classpath (pulled in
 * transitively via jackson-dataformat-csv). Same reasoning as {@code ShipmentCsvParser}'s own {@code CsvMapper} field.
 */
@Component
public class GfmBidClient extends RetryingHttpClient {

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
        URI getBidUri = UriComponentsBuilder.fromUriString(this.gfmProperties.gfmBaseUrl())
                .path("/atr/getBid")
                .queryParam("id", offerId)
                .queryParam("readOnly", true)
                .queryParam("requestTime", Instant.now().toEpochMilli())
                .build()
                .toUri();

        // In-page XHR fired while the browser is still on /atr/shipment (that's the page listing shipments to click
        // into), not a fresh navigation - see GfmFetchMetadataHeaders for why the header shape differs from
        // FetchAndLoadShipmentsTasklet's navigation calls.
        String rawResponse = retrying(
                () -> GfmFetchMetadataHeaders.forXhr(this.gfmRestClient
                                .get()
                                .uri(getBidUri)
                                .header(HttpHeaders.REFERER, this.gfmProperties.gfmBaseUrl() + "/atr/shipment"))
                        .retrieve()
                        .body(String.class),
                this.retryBackoff);

        Bid bid = parseBid(rawResponse, offerId);
        GfmShipment shipment = shipmentOf(bid);

        return new ShipmentDetailRow(
                offerId,
                bid.getTotalAmount(),
                bid.getLineHaulCost(),
                bid.getRateUsed(),
                bid.getScac(),
                bid.getScacName(),
                bid.getTenderNumber(),
                bid.getEquipmentDesc(),
                shipment.getRequestorName(),
                shipment.getRequestorEmail(),
                rawResponse,
                null);
    }

    private Bid parseBid(String rawResponse, long offerId) {
        GfmGetBidResponse response;

        try {
            response = this.objectMapper.readValue(rawResponse, GfmGetBidResponse.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to parse getBid response for offer " + offerId, e);
        }

        Bid bid = response.getBid();
        return bid != null ? bid : new Bid();
    }

    private GfmShipment shipmentOf(Bid bid) {
        Equipment equipment = bid.getEquipment();
        GfmShipment shipment = equipment != null ? equipment.getShipment() : null;
        return shipment != null ? shipment : new GfmShipment();
    }
}
