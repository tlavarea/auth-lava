package com.lava.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lava.boot.autoconfigure.app.AuthThrottleProperties;
import com.lava.exception.TooManyRequestsException;
import com.lava.model.database.tables.pojos.AuthThrottle;
import com.lava.model.throttle.AuthThrottleScope;
import com.lava.repository.AuthThrottleRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceImplTest {

    @Mock
    private AuthThrottleRepository authThrottleRepository;

    private AuthThrottleProperties authThrottleProperties;
    private RateLimitServiceImpl service;

    @BeforeEach
    void setUp() {
        this.authThrottleProperties = new AuthThrottleProperties(5, Duration.ofMinutes(15), 5, Duration.ofMinutes(15));
        this.service = new RateLimitServiceImpl(this.authThrottleRepository, this.authThrottleProperties);
    }

    @Test
    void checkNotLocked_noExistingRow_doesNotThrow() {
        when(this.authThrottleRepository.find("login", "user@example.com")).thenReturn(Optional.empty());

        this.service.checkNotLocked(AuthThrottleScope.LOGIN, "user@example.com");
    }

    @Test
    void checkNotLocked_lockedUntilInFuture_throwsTooManyRequests() {
        AuthThrottle locked = throttleRow(1, LocalDateTime.now().plusMinutes(5));
        when(this.authThrottleRepository.find("login", "user@example.com")).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> this.service.checkNotLocked(AuthThrottleScope.LOGIN, "user@example.com"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void checkNotLocked_lockedUntilInPast_doesNotThrow() {
        AuthThrottle expired = throttleRow(5, LocalDateTime.now().minusMinutes(1));
        when(this.authThrottleRepository.find("login", "user@example.com")).thenReturn(Optional.of(expired));

        this.service.checkNotLocked(AuthThrottleScope.LOGIN, "user@example.com");
    }

    @Test
    void recordFailure_belowThreshold_upsertsWithoutLockingAndKeepsCounting() {
        AuthThrottle existing = throttleRow(2, null);
        when(this.authThrottleRepository.find("login", "user@example.com")).thenReturn(Optional.of(existing));

        this.service.recordFailure(AuthThrottleScope.LOGIN, "user@example.com");

        verify(this.authThrottleRepository).upsertFailure(eq("login"), eq("user@example.com"), eq(3), isNull(), any());
    }

    @Test
    void recordFailure_reachesThreshold_setsLockedUntil() {
        AuthThrottle existing = throttleRow(4, null);
        when(this.authThrottleRepository.find("login", "user@example.com")).thenReturn(Optional.of(existing));

        this.service.recordFailure(AuthThrottleScope.LOGIN, "user@example.com");

        verify(this.authThrottleRepository)
                .upsertFailure(eq("login"), eq("user@example.com"), eq(5), any(LocalDateTime.class), any());
    }

    @Test
    void recordFailure_noExistingRow_startsCountAtOne() {
        when(this.authThrottleRepository.find("mfa_verify", "7")).thenReturn(Optional.empty());

        this.service.recordFailure(AuthThrottleScope.MFA_VERIFY, "7");

        verify(this.authThrottleRepository).upsertFailure(eq("mfa_verify"), eq("7"), eq(1), isNull(), any());
    }

    @Test
    void recordFailure_priorLockExpired_treatsAsFreshWindowStartingAtOne() {
        AuthThrottle expiredLock = throttleRow(5, LocalDateTime.now().minusSeconds(1));
        when(this.authThrottleRepository.find("login", "user@example.com")).thenReturn(Optional.of(expiredLock));

        this.service.recordFailure(AuthThrottleScope.LOGIN, "user@example.com");

        verify(this.authThrottleRepository).upsertFailure(eq("login"), eq("user@example.com"), eq(1), isNull(), any());
    }

    @Test
    void recordSuccess_clearsRow() {
        this.service.recordSuccess(AuthThrottleScope.MFA_VERIFY, "7");

        verify(this.authThrottleRepository).clear("mfa_verify", "7");
        verify(this.authThrottleRepository, never()).upsertFailure(any(), any(), anyInt(), any(), any());
    }

    private static AuthThrottle throttleRow(int failedCount, LocalDateTime lockedUntil) {
        return new AuthThrottle(1L, "login", "user@example.com", failedCount, lockedUntil, LocalDateTime.now());
    }
}
