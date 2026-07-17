package com.lava.swexpedited.batch.vektor;

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
public class VektorAuthenticator extends VektorClient {

    private final RestClient vektorRestClient;
    private final VektorProperties vektorProperties;

    public VektorAuthenticator(
            @Qualifier("vektorRestClient") RestClient vektorRestClient, VektorProperties vektorProperties) {
        this.vektorRestClient = vektorRestClient;
        this.vektorProperties = vektorProperties;
    }

    public String authenticate() {
        byte[] requestBody = VektorGrpcWeb.encodeUnaryRequest(new VektorGrpcWeb.Writer()
                .writeString(1, this.vektorProperties.username())
                .writeString(2, this.vektorProperties.password())
                .writeVarint(3, 1));
        ResponseEntity<byte[]> response = this.vektorRestClient
                .post()
                .uri("/carrier/dashboard/auth/envoy/VerificationCodeService/VerificationCodeSend")
                .body(requestBody)
                .retrieve()
                .toEntity(byte[].class);

        return VektorGrpcWeb.decodeUnaryResponse(requireBody(response, "Login"))
                .getString(2)
                .orElseThrow(() -> new IllegalStateException("Vektor login response did not contain a JWT"));
    }
}
