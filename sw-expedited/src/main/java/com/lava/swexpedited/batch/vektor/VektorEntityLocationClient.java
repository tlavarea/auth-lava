package com.lava.swexpedited.batch.vektor;

import com.lava.swexpedited.vektor.VektorDriverLocationRow;
import com.lava.swexpedited.vektor.VektorGrpcWeb;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Fetches every driver's live location for the company via {@code EntityLocation/GetAll} - reverse-engineered from a
 * real captured request/response pair. Unlike {@code Manifests/Get}/{@code Drivers/Get}, this call takes no request
 * fields at all (confirmed against the real capture - {@link VektorGrpcWeb}'s framing already supports a genuinely
 * empty message, this is just the first client here to actually send one). The response's field 3 (repeated) is what
 * this app cares about - a live/recent-location feed keyed directly by Vektor's own {@code driver_id}. Response fields
 * 1, 2, and 4 (other repeated arrays, apparently per-vehicle telemetry and a small set of other locations) are unmapped
 * - not needed for driver location.
 */
@Component
public class VektorEntityLocationClient extends VektorClient {

    private final RestClient vektorRestClient;
    private final Duration retryBackoff;

    public VektorEntityLocationClient(
            @Qualifier("vektorRestClient") RestClient vektorRestClient,
            @Value("${vektor.retry-backoff:5s}") Duration retryBackoff) {
        this.vektorRestClient = vektorRestClient;
        this.retryBackoff = retryBackoff;
    }

    public List<VektorDriverLocationRow> fetchAll(String jwt, String companyId) {
        byte[] requestBody = VektorGrpcWeb.encodeUnaryRequest(new VektorGrpcWeb.Writer());
        ResponseEntity<byte[]> response = retrying(
                () -> this.vektorRestClient
                        .post()
                        .uri("/carrier/dashboard/core/envoy/EntityLocation/GetAll")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                        .header("company_id", companyId)
                        .body(requestBody)
                        .retrieve()
                        .toEntity(byte[].class),
                this.retryBackoff);

        return VektorGrpcWeb.decodeUnaryResponse(requireBody(response, "EntityLocation/GetAll")).getMessages(3).stream()
                .map(this::toRow)
                .toList();
    }

    private VektorDriverLocationRow toRow(VektorGrpcWeb.Message entry) {
        return new VektorDriverLocationRow(
                entry.getString(1).orElse(null),
                entry.getDouble(3).map(BigDecimal::valueOf).orElse(null),
                entry.getDouble(4).map(BigDecimal::valueOf).orElse(null),
                entry.getDouble(5).map(BigDecimal::valueOf).orElse(null),
                entry.getVarint(6)
                        .map(ms -> LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC))
                        .orElse(null),
                entry.getString(7).orElse(null));
    }
}
