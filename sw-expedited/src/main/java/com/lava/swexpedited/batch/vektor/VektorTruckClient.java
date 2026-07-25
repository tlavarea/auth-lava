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
 * Fetches Vektor's full truck roster via {@code Trucks/Get} - reverse-engineered from a real captured request/ response
 * pair. The request body's single field (a varint {@code 1}) and the response's field layout follow the same shape as
 * {@code VektorDriverClient#fetchDrivers}/{@code VektorTimeOffClient}; its exact purpose is unconfirmed but sent as-is
 * since it's exactly what Vektor's own UI sends. Returns raw {@link VektorGrpcWeb.Message}s rather than a typed model,
 * since there's no schema to generate one from - {@code VektorTruckMapper} is what knows which field numbers mean what.
 */
@Component
public class VektorTruckClient extends VektorClient {

    private final RestClient vektorRestClient;
    private final Duration retryBackoff;

    public VektorTruckClient(
            @Qualifier("vektorRestClient") RestClient vektorRestClient,
            @Value("${vektor.retry-backoff:5s}") Duration retryBackoff) {
        this.vektorRestClient = vektorRestClient;
        this.retryBackoff = retryBackoff;
    }

    public List<VektorGrpcWeb.Message> fetchTrucks(String jwt, String companyId) {
        byte[] requestBody = VektorGrpcWeb.encodeUnaryRequest(new VektorGrpcWeb.Writer().writeVarint(1, 1));
        ResponseEntity<byte[]> response = retrying(
                () -> this.vektorRestClient
                        .post()
                        .uri("/carrier/dashboard/fleet/envoy/Trucks/Get")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                        .header("company_id", companyId)
                        .body(requestBody)
                        .retrieve()
                        .toEntity(byte[].class),
                this.retryBackoff);

        return VektorGrpcWeb.decodeUnaryResponse(requireBody(response, "Trucks/Get"))
                .getMessages(1);
    }
}
