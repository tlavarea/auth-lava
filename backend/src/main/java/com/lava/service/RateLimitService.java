package com.lava.service;

import com.lava.model.throttle.AuthThrottleScope;

public interface RateLimitService {

    /**
     * Throws {@link com.lava.exception.TooManyRequestsException} if the given scope+identifier is currently locked out
     * from a prior run of failed attempts. Callers should check this before attempting the underlying credential/code
     * check.
     *
     * @param scope - the throttle scope.
     * @param identifier - the value being throttled (email for login, user id for MFA verify).
     */
    void checkNotLocked(AuthThrottleScope scope, String identifier);

    /**
     * Records a failed attempt for the given scope+identifier, locking it out once the scope's configured max-attempts
     * threshold is reached.
     *
     * @param scope - the throttle scope.
     * @param identifier - the value being throttled.
     */
    void recordFailure(AuthThrottleScope scope, String identifier);

    /**
     * Clears any failure/lockout state for the given scope+identifier, called after a successful attempt.
     *
     * @param scope - the throttle scope.
     * @param identifier - the value being throttled.
     */
    void recordSuccess(AuthThrottleScope scope, String identifier);
}
