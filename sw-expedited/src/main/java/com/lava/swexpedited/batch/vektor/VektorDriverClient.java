package com.lava.swexpedited.batch.vektor;

import com.lava.swexpedited.vektor.VektorGrpcWeb;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Fetches Vektor's driver roster via {@code Drivers/Get} and resolves it to a UUID-&gt;full-name map. Manifests only
 * carry a driver UUID (see {@code VektorManifestMapper}), never a name - this is the one call needed to make that UUID
 * meaningful downstream. The request body's single field (a varint {@code 1}) and the response's field layout were both
 * reverse-engineered from a real captured, authenticated request/response pair (see the Vektor manifest sync plan); its
 * exact purpose is unconfirmed but sent as-is since it's exactly what Vektor's own UI sends.
 */
@Component
public class VektorDriverClient extends VektorClient {

    private final RestClient vektorRestClient;
    private final Duration retryBackoff;

    public VektorDriverClient(
            @Qualifier("vektorRestClient") RestClient vektorRestClient,
            @Value("${vektor.retry-backoff:5s}") Duration retryBackoff) {
        this.vektorRestClient = vektorRestClient;
        this.retryBackoff = retryBackoff;
    }

    public Map<String, String> fetchDriverNamesById(String jwt, String companyId) {
        byte[] requestBody = VektorGrpcWeb.encodeUnaryRequest(new VektorGrpcWeb.Writer().writeVarint(1, 1));
        ResponseEntity<byte[]> response = retrying(
                () -> this.vektorRestClient
                        .post()
                        .uri("/carrier/dashboard/fleet/envoy/Drivers/Get")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                        .header("company_id", companyId)
                        .body(requestBody)
                        .retrieve()
                        .toEntity(byte[].class),
                this.retryBackoff);
        Map<String, String> namesById = new LinkedHashMap<>();

        for (VektorGrpcWeb.Message driver : VektorGrpcWeb.decodeUnaryResponse(requireBody(response, "Drivers/Get"))
                .getMessages(1)) {
            driver.getString(1)
                    .ifPresent(id -> driver.getString(35).ifPresent(fullName -> namesById.put(id, fullName)));
        }

        return namesById;
    }
}
