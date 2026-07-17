package com.lava.swexpedited.batch;

import com.lava.swexpedited.vektor.VektorGrpcWeb;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

/**
 * Fetches Vektor's driver roster via {@code Drivers/Get} and resolves it to a UUID-&gt;full-name map. Manifests only
 * carry a driver UUID (see {@code VektorManifestMapper}), never a name - this is the one call needed to make that UUID
 * meaningful downstream. The request body's single field (a varint {@code 1}) and the response's field layout were both
 * reverse-engineered from a real captured, authenticated request/response pair (see the Vektor manifest sync plan); its
 * exact purpose is unconfirmed but sent as-is since it's exactly what Vektor's own UI sends.
 */
@Component
public class VektorDriverClient {

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

        ResponseEntity<byte[]> response = retrying(() -> vektorRestClient
                .post()
                .uri("/carrier/dashboard/fleet/envoy/Drivers/Get")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .header("company_id", companyId)
                .body(requestBody)
                .retrieve()
                .toEntity(byte[].class));

        Map<String, String> namesById = new LinkedHashMap<>();
        for (VektorGrpcWeb.Message driver : VektorGrpcWeb.decodeUnaryResponse(requireBody(response, "Drivers/Get"))
                .getMessages(1)) {
            driver.getString(1)
                    .ifPresent(id -> driver.getString(35).ifPresent(fullName -> namesById.put(id, fullName)));
        }
        return namesById;
    }

    /**
     * A 2xx response with no body isn't handled by {@link VektorGrpcWeb#decodeUnaryResponse} (it expects at least a
     * trailer frame) - it's most likely a gRPC-Web "Trailers-Only" response, where an immediate gRPC-level error
     * (invalid argument, permission denied, etc.) is reported via HTTP headers instead of a body frame. Surfacing the
     * status and those headers here beats the {@link NullPointerException} decodeUnaryResponse would otherwise throw.
     */
    private byte[] requireBody(ResponseEntity<byte[]> response, String rpcName) {
        byte[] body = response.getBody();
        if (body != null && body.length > 0) {
            return body;
        }
        throw new VektorGrpcWeb.VektorGrpcWebException("Vektor " + rpcName + " returned an empty response body (HTTP "
                + response.getStatusCode() + ", grpc-status="
                + response.getHeaders().getFirst("grpc-status")
                + ", grpc-message=" + response.getHeaders().getFirst("grpc-message") + ")");
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
