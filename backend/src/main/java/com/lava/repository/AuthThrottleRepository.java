package com.lava.repository;

import com.lava.model.database.tables.pojos.AuthThrottle;
import java.time.LocalDateTime;
import java.util.Optional;

public interface AuthThrottleRepository {

    /**
     * Clears any failure/lockout state for the given scope+identifier, called after a successful attempt.
     *
     * @param scope - the throttle scope (e.g. "login", "mfa_verify").
     * @param identifier - the value being throttled (email for login, user id for MFA verify).
     */
    void clear(String scope, String identifier);

    Optional<AuthThrottle> find(String scope, String identifier);

    /**
     * Creates or overwrites the failure-tracking row for the given scope+identifier with the given failed-attempt count
     * and lockout expiry, both computed by the caller (this repository has no knowledge of configured thresholds).
     *
     * @param scope - the throttle scope.
     * @param identifier - the value being throttled.
     * @param failedCount - the new failed-attempt count.
     * @param lockedUntil - when the lockout (if any) expires; null if the threshold hasn't been reached.
     * @param updatedAt - when this failure was recorded.
     */
    void upsertFailure(
            String scope, String identifier, int failedCount, LocalDateTime lockedUntil, LocalDateTime updatedAt);
}
