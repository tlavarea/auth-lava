package com.lava.swexpedited.batch.vektor;

import com.lava.swexpedited.vektor.VektorEtaSnapshotRow;
import com.lava.swexpedited.vektor.VektorGrpcWeb;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Fetches a manifest's full ETA calculation history via {@code Manifests/TruckEtaStatesGet}, reverse-engineered from a
 * real captured request/response pair - the same {@code {1: manifestId}} single-field request shape as
 * {@code Manifests/TruckRouteRetrieve}. The response's field 1 (repeated) is a list of per-stop ETA snapshots (see
 * {@link VektorEtaSnapshotRow}); several fields on each snapshot (2.1, 6, 12, 19, 20, 22, 23, 24, 25) are still
 * unconfirmed or not needed here and are deliberately left unmapped.
 */
@Component
public class VektorTruckEtaStatesClient extends VektorClient {

    private static final DateTimeFormatter ESTIMATED_ARRIVAL_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestClient vektorRestClient;
    private final Duration retryBackoff;

    public VektorTruckEtaStatesClient(
            @Qualifier("vektorRestClient") RestClient vektorRestClient,
            @Value("${vektor.retry-backoff:5s}") Duration retryBackoff) {
        this.vektorRestClient = vektorRestClient;
        this.retryBackoff = retryBackoff;
    }

    public List<VektorEtaSnapshotRow> fetch(String jwt, String companyId, String manifestId) {
        byte[] requestBody = VektorGrpcWeb.encodeUnaryRequest(new VektorGrpcWeb.Writer().writeString(1, manifestId));
        ResponseEntity<byte[]> response = retrying(
                () -> this.vektorRestClient
                        .post()
                        .uri("/carrier/dashboard/core/envoy/Manifests/TruckEtaStatesGet")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                        .header("company_id", companyId)
                        .body(requestBody)
                        .retrieve()
                        .toEntity(byte[].class),
                this.retryBackoff);

        return VektorGrpcWeb.decodeUnaryResponse(requireBody(response, "Manifests/TruckEtaStatesGet"))
                .getMessages(1)
                .stream()
                .map(this::toRow)
                .toList();
    }

    private VektorEtaSnapshotRow toRow(VektorGrpcWeb.Message snapshot) {
        String targetStopId =
                snapshot.getMessage(2).flatMap(target -> target.getString(2)).orElse(null);
        return new VektorEtaSnapshotRow(
                targetStopId,
                snapshot.getVarint(5).map(Long::intValue).orElse(0),
                snapshot.getDouble(9).map(BigDecimal::valueOf).orElse(null),
                snapshot.getDouble(10).map(BigDecimal::valueOf).orElse(null),
                snapshot.getDouble(15).map(BigDecimal::valueOf).orElse(null),
                snapshot.getVarint(13).map(Long::intValue).orElse(0),
                snapshot.getString(11).map(this::parseDateTime).orElse(null));
    }

    private LocalDateTime parseDateTime(String text) {
        return LocalDateTime.parse(text, ESTIMATED_ARRIVAL_FORMAT);
    }
}
