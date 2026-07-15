package com.lava.swexpedited.boot.autoconfigure.app;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

/**
 * Credentials and endpoints for the DoD GFM/ATR shipment system: an IdentTrust client certificate (PKCS12 keystore)
 * used for mutual-TLS login, and a JKS truststore for the GFM/eta-teams/mps-kmis server chain. keyStore/trustStore
 * point at files outside the repo via GFM_KEYSTORE_PATH/GFM_TRUSTSTORE_PATH - never commit the actual cert material.
 * The three base URLs default to the real hosts but are configurable so tests can point them at a stub server instead
 * of hardcoding hostnames into the request-building code.
 */
@ConfigurationProperties(prefix = "gfm")
@Validated
public record GfmProperties(
        Resource keyStore,
        String keyStorePassword,
        Resource trustStore,
        String trustStorePassword,
        @NotBlank String clientId,
        @NotBlank String etaTeamsBaseUrl,
        @NotBlank String mpsKmisBaseUrl,
        @NotBlank String gfmBaseUrl) {}
