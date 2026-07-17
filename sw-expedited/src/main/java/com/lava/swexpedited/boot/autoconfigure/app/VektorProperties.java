package com.lava.swexpedited.boot.autoconfigure.app;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Credentials and endpoint for app.vektortms.com's internal gRPC-Web backend (Vektor has no public API - see
 * VektorGrpcWeb). username/password/companyId deliberately have no {@code @NotBlank} and no default, same reasoning as
 * {@code SamsaraProperties.apiToken()}: there's no {@code @Profile("!test")}-excluded bean construction to defer the
 * failure to here, so validating credentials eagerly would fail the entire application context for any developer
 * running sw-expedited locally without real Vektor credentials, not just the Vektor sync. Blank credentials instead
 * flow through to a real (rejected) login request the first time a sync job actually runs. companyId is a fixed
 * per-account UUID (this sync only ever targets one Vektor company) required as a {@code company_id} header on every
 * company-scoped call ({@code Manifests/Get}, {@code Drivers/Get}, etc.) - captured once from a real
 * {@code Account/CompanyGet} response rather than re-fetched at runtime, since it never changes for this account.
 *
 * <p>syncedStatuses is which Manifests/Get {@code effective_status} values to sync - a product decision (do we only
 * want currently-"Traveling" manifests, or also look ahead at dispatched/planned ones?) more than a technical one, so
 * it's configurable rather than hardcoded to the one status this was validated against during investigation.
 */
@ConfigurationProperties(prefix = "vektor")
@Validated
public record VektorProperties(
        String username,
        String password,
        String companyId,
        @NotBlank String baseUrl,
        Duration retryBackoff,
        List<String> syncedStatuses) {}
