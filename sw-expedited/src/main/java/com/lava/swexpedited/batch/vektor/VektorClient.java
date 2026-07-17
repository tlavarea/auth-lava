package com.lava.swexpedited.batch.vektor;

import com.lava.swexpedited.batch.RetryingHttpClient;
import com.lava.swexpedited.vektor.VektorGrpcWeb;
import org.springframework.http.ResponseEntity;

public abstract class VektorClient extends RetryingHttpClient {

    /**
     * A 2xx response with no body isn't handled by {@link VektorGrpcWeb#decodeUnaryResponse} (it expects at least a
     * trailer frame) - it's most likely a gRPC-Web "Trailers-Only" response, where an immediate gRPC-level error
     * (invalid argument, permission denied, etc.) is reported via HTTP headers instead of a body frame. Surfacing the
     * status and those headers here beats the {@link NullPointerException} decodeUnaryResponse would otherwise throw.
     */
    protected byte[] requireBody(ResponseEntity<byte[]> response, String rpcName) {
        byte[] body = response.getBody();

        if (body != null && body.length > 0) {
            return body;
        }

        throw new VektorGrpcWeb.VektorGrpcWebException("Vektor " + rpcName + " returned an empty response body (HTTP "
                + response.getStatusCode() + ", grpc-status="
                + response.getHeaders().getFirst("grpc-status")
                + ", grpc-message=" + response.getHeaders().getFirst("grpc-message") + ")");
    }
}
