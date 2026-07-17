package com.lava.swexpedited.batch;

import com.lava.swexpedited.vektor.VektorGrpcWeb;
import java.time.Duration;
import java.util.List;
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
 * Fetches Vektor manifests via {@code Manifests/Get}, filtered to the configured {@code effective_status} values and
 * sorted by first-stop appointment start (most recent first) - the exact request shape Vektor's own UI sends when a
 * dispatcher filters the manifest list, reverse-engineered from a real captured request (see the Vektor manifest sync
 * plan). Returns raw {@link VektorGrpcWeb.Message}s rather than a typed model, since there's no schema to generate one
 * from - {@code VektorManifestMapper} is what knows which field numbers mean what.
 */
@Component
public class VektorManifestClient {

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

        ResponseEntity<byte[]> response = retrying(() -> vektorRestClient
                .post()
                .uri("/carrier/dashboard/core/envoy/Manifests/Get")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .header("company_id", companyId)
                .body(VektorGrpcWeb.encodeUnaryRequest(request))
                .retrieve()
                .toEntity(byte[].class));

        return VektorGrpcWeb.decodeUnaryResponse(requireBody(response, "Manifests/Get"))
                .getMessages(3);
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
