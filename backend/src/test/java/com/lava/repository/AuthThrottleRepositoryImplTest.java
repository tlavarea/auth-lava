package com.lava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.model.database.tables.pojos.AuthThrottle;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AuthThrottleRepositoryImplTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private AuthThrottleRepository authThrottleRepository;

    @Test
    void find_notFound_isEmpty() {
        assertThat(this.authThrottleRepository.find("login", "missing@example.com"))
                .isEmpty();
    }

    @Test
    void find_isCaseInsensitive() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        this.authThrottleRepository.upsertFailure("login", "Mixed-Case@Example.com", 1, null, now);

        Optional<AuthThrottle> found = this.authThrottleRepository.find("login", "mixed-case@example.com");

        assertThat(found).isPresent();
    }

    @Test
    void upsertFailure_noExistingRow_insertsFreshRow() {
        LocalDateTime now = LocalDateTime.now().withNano(0);

        this.authThrottleRepository.upsertFailure("login", "throttle-1@example.com", 1, null, now);

        AuthThrottle row = this.authThrottleRepository
                .find("login", "throttle-1@example.com")
                .orElseThrow();
        assertThat(row.failedCount()).isEqualTo(1);
        assertThat(row.lockedUntil()).isNull();
    }

    @Test
    void upsertFailure_existingRow_overwritesCountAndLockedUntil() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        this.authThrottleRepository.upsertFailure("login", "throttle-2@example.com", 1, null, now);

        LocalDateTime lockedUntil = now.plusMinutes(15);
        this.authThrottleRepository.upsertFailure("login", "throttle-2@example.com", 5, lockedUntil, now);

        AuthThrottle row = this.authThrottleRepository
                .find("login", "throttle-2@example.com")
                .orElseThrow();
        assertThat(row.failedCount()).isEqualTo(5);
        assertThat(row.lockedUntil()).isEqualTo(lockedUntil);
    }

    @Test
    void upsertFailure_differentScopesForSameIdentifier_trackedIndependently() {
        LocalDateTime now = LocalDateTime.now().withNano(0);

        this.authThrottleRepository.upsertFailure("login", "42", 3, null, now);
        this.authThrottleRepository.upsertFailure("mfa_verify", "42", 1, null, now);

        assertThat(this.authThrottleRepository.find("login", "42").orElseThrow().failedCount())
                .isEqualTo(3);
        assertThat(this.authThrottleRepository
                        .find("mfa_verify", "42")
                        .orElseThrow()
                        .failedCount())
                .isEqualTo(1);
    }

    @Test
    void clear_removesRow() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        this.authThrottleRepository.upsertFailure("login", "throttle-3@example.com", 1, null, now);

        this.authThrottleRepository.clear("login", "throttle-3@example.com");

        assertThat(this.authThrottleRepository.find("login", "throttle-3@example.com"))
                .isEmpty();
    }
}
