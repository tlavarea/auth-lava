package com.lava.service;

import com.lava.boot.autoconfigure.app.AuthThrottleProperties;
import com.lava.exception.TooManyRequestsException;
import com.lava.logging.LogSanitizer;
import com.lava.model.database.tables.pojos.AuthThrottle;
import com.lava.model.throttle.AuthThrottleScope;
import com.lava.repository.AuthThrottleRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Slf4j
@Service
@Transactional(readOnly = true)
public class RateLimitServiceImpl implements RateLimitService {

    private final AuthThrottleRepository authThrottleRepository;
    private final AuthThrottleProperties authThrottleProperties;

    @Override
    public void checkNotLocked(AuthThrottleScope scope, String identifier) {
        LocalDateTime now = LocalDateTime.now();
        this.authThrottleRepository
                .find(scope.dbValue(), identifier)
                .filter(row -> row.lockedUntil() != null && row.lockedUntil().isAfter(now))
                .ifPresent(row -> {
                    log.warn(
                            "checkNotLocked::blocked, scope: {}, identifier: {}, lockedUntil: {}",
                            scope,
                            LogSanitizer.sanitize(identifier),
                            row.lockedUntil());
                    throw new TooManyRequestsException("Too many failed attempts - try again later");
                });
    }

    // REQUIRES_NEW is deliberate: recordFailure is always called from inside a caller's
    // @Transactional method (AuthServiceImpl#login, MfaServiceImpl#verifyCode) that goes on to
    // rethrow the auth/verification exception. Joining that transaction would mean this write
    // gets rolled back along with everything else once the caller rethrows, silently defeating
    // the whole point of recording the failure. Running in its own transaction lets it commit
    // independently of the caller's outcome.
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(AuthThrottleScope scope, String identifier) {
        LocalDateTime now = LocalDateTime.now();
        Optional<AuthThrottle> existing = this.authThrottleRepository.find(scope.dbValue(), identifier);
        boolean lockExpired = existing.map(AuthThrottle::lockedUntil)
                .filter(lockedUntil -> !lockedUntil.isAfter(now))
                .isPresent();
        int currentCount =
                existing.isEmpty() || lockExpired ? 0 : existing.get().failedCount();
        int newCount = currentCount + 1;
        int maxAttempts = this.maxAttempts(scope);
        LocalDateTime lockedUntil = newCount >= maxAttempts ? now.plus(this.lockoutDuration(scope)) : null;

        this.authThrottleRepository.upsertFailure(scope.dbValue(), identifier, newCount, lockedUntil, now);

        if (lockedUntil != null) {
            log.warn(
                    "recordFailure::locked out, scope: {}, identifier: {}, until: {}",
                    scope,
                    LogSanitizer.sanitize(identifier),
                    lockedUntil);
        } else {
            log.info(
                    "recordFailure::failed attempt {}/{}, scope: {}, identifier: {}",
                    newCount,
                    maxAttempts,
                    scope,
                    LogSanitizer.sanitize(identifier));
        }
    }

    // Same REQUIRES_NEW reasoning as recordFailure: callers do further work after this returns
    // (issuing tokens, etc.) that could still fail and roll back their transaction - the cleared
    // throttle state shouldn't be undone by that.
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(AuthThrottleScope scope, String identifier) {
        this.authThrottleRepository.clear(scope.dbValue(), identifier);
    }

    private Duration lockoutDuration(AuthThrottleScope scope) {
        return switch (scope) {
            case LOGIN -> this.authThrottleProperties.loginLockoutDuration();
            case MFA_VERIFY -> this.authThrottleProperties.mfaLockoutDuration();
        };
    }

    private int maxAttempts(AuthThrottleScope scope) {
        return switch (scope) {
            case LOGIN -> this.authThrottleProperties.loginMaxAttempts();
            case MFA_VERIFY -> this.authThrottleProperties.mfaMaxAttempts();
        };
    }
}
