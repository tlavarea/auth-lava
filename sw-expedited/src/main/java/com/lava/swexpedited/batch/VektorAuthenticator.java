package com.lava.swexpedited.batch;

import com.lava.swexpedited.boot.autoconfigure.app.VektorProperties;
import com.lava.swexpedited.vektor.VektorGrpcWeb;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Logs into app.vektortms.com's gRPC-Web backend. Despite its name (a real captured request/response pair, byte for
 * byte - see the Vektor manifest sync plan), {@code VerificationCodeService/VerificationCodeSend} is the actual
 * email+password login call, not a 2FA/OTP step - the response's field 2 is a self-contained JWT (HS256, `user_id`/
 * `iat`/`exp` claims, no shared secret needed on our side) used as the {@code Authorization: Bearer} value on every
 * other Vektor call this app makes.
 */
@Component
public class VektorAuthenticator {

    private final RestClient vektorRestClient;
    private final VektorProperties vektorProperties;

    public VektorAuthenticator(
            @Qualifier("vektorRestClient") RestClient vektorRestClient, VektorProperties vektorProperties) {
        this.vektorRestClient = vektorRestClient;
        this.vektorProperties = vektorProperties;
    }

    public String authenticate() {
        byte[] requestBody = VektorGrpcWeb.encodeUnaryRequest(new VektorGrpcWeb.Writer()
                .writeString(1, vektorProperties.username())
                .writeString(2, vektorProperties.password())
                .writeVarint(3, 1));

        ResponseEntity<byte[]> response = vektorRestClient
                .post()
                .uri("/carrier/dashboard/auth/envoy/VerificationCodeService/VerificationCodeSend")
                .body(requestBody)
                .retrieve()
                .toEntity(byte[].class);

        return VektorGrpcWeb.decodeUnaryResponse(requireBody(response))
                .getString(2)
                .orElseThrow(() -> new IllegalStateException("Vektor login response did not contain a JWT"));
    }

    /**
     * A 2xx response with no body isn't handled by {@link VektorGrpcWeb#decodeUnaryResponse} (it expects at least a
     * trailer frame) - it's most likely a gRPC-Web "Trailers-Only" response, where an immediate gRPC-level error
     * (invalid argument, permission denied, etc.) is reported via HTTP headers instead of a body frame. Surfacing the
     * status and those headers here beats the {@link NullPointerException} decodeUnaryResponse would otherwise throw.
     */
    private byte[] requireBody(ResponseEntity<byte[]> response) {
        byte[] body = response.getBody();
        if (body != null && body.length > 0) {
            return body;
        }
        throw new VektorGrpcWeb.VektorGrpcWebException(
                "Vektor VerificationCodeService/VerificationCodeSend returned an empty response body (HTTP "
                        + response.getStatusCode() + ", grpc-status="
                        + response.getHeaders().getFirst("grpc-status")
                        + ", grpc-message=" + response.getHeaders().getFirst("grpc-message") + ")");
    }
}
