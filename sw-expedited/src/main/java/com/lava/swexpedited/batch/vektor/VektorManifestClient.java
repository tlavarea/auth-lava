package com.lava.swexpedited.batch.vektor;

import com.lava.swexpedited.vektor.VektorGrpcWeb;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Fetches Vektor manifests via {@code Manifests/Get}, filtered to the configured {@code effective_status} values and
 * sorted by first-stop appointment start (most recent first) - the exact request shape Vektor's own UI sends when a
 * dispatcher filters the manifest list, reverse-engineered from a real captured request (see the Vektor manifest sync
 * plan). Returns raw {@link VektorGrpcWeb.Message}s rather than a typed model, since there's no schema to generate one
 * from - {@code VektorManifestMapper} is what knows which field numbers mean what.
 */
@Component
public class VektorManifestClient extends VektorClient {

    private final RestClient vektorRestClient;
    private final Duration retryBackoff;

    public VektorManifestClient(
            @Qualifier("vektorRestClient") RestClient vektorRestClient,
            @Value("${vektor.retry-backoff:5s}") Duration retryBackoff) {
        this.vektorRestClient = vektorRestClient;
        this.retryBackoff = retryBackoff;
    }

    public List<VektorGrpcWeb.Message> fetchManifests(String jwt, String companyId, List<String> effectiveStatuses) {
        VektorGrpcWeb.Writer statusValues = new VektorGrpcWeb.Writer();

        for (String status : effectiveStatuses) {
            statusValues.writeString(1, status);
        }

        VektorGrpcWeb.Writer statusValuesWrapper = new VektorGrpcWeb.Writer().writeMessage(1, statusValues);
        VektorGrpcWeb.Writer filter = new VektorGrpcWeb.Writer()
                .writeString(1, "effective_status")
                .writeString(2, "is")
                .writeMessage(3, statusValuesWrapper);
        VektorGrpcWeb.Writer request = new VektorGrpcWeb.Writer()
                .writeVarint(2, 50)
                .writeString(3, "first_stop_appointment_start_datetime")
                .writeString(4, "desc")
                .writeMessage(5, filter);
        ResponseEntity<byte[]> response = retrying(
                () -> this.vektorRestClient
                        .post()
                        .uri("/carrier/dashboard/core/envoy/Manifests/Get")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                        .header("company_id", companyId)
                        .body(VektorGrpcWeb.encodeUnaryRequest(request))
                        .retrieve()
                        .toEntity(byte[].class),
                this.retryBackoff);

        return VektorGrpcWeb.decodeUnaryResponse(requireBody(response, "Manifests/Get"))
                .getMessages(3);
    }
}
