package com.lava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.model.database.tables.pojos.PendingRegistration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PendingRegistrationRepositoryImplTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private PendingRegistrationRepository pendingRegistrationRepository;

    @Test
    void upsertCode_noExistingRow_insertsFreshRow() {
        LocalDateTime now = LocalDateTime.now().withNano(0);

        PendingRegistration inserted = this.pendingRegistrationRepository.upsertCode(
                "pending-1@example.com", "hash-1", now, now.plusMinutes(5));

        assertThat(inserted.email()).isEqualTo("pending-1@example.com");
        assertThat(inserted.codeHash()).isEqualTo("hash-1");
        assertThat(inserted.attemptCount()).isZero();
        assertThat(inserted.verifiedAt()).isNull();
    }

    @Test
    void upsertCode_existingRow_overwritesCodeAndResetsAttemptsAndVerification() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        PendingRegistration first = this.pendingRegistrationRepository.upsertCode(
                "pending-2@example.com", "hash-old", now, now.plusMinutes(5));
        this.pendingRegistrationRepository.incrementAttempt(first.id());
        this.pendingRegistrationRepository.markVerified(first.id(), now);

        PendingRegistration second = this.pendingRegistrationRepository.upsertCode(
                "pending-2@example.com", "hash-new", now.plusSeconds(1), now.plusMinutes(6));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.codeHash()).isEqualTo("hash-new");
        assertThat(second.attemptCount()).isZero();
        assertThat(second.verifiedAt()).isNull();
    }

    @Test
    void findByEmail_notFound_isEmpty() {
        assertThat(this.pendingRegistrationRepository.findByEmail("missing@example.com"))
                .isEmpty();
    }

    @Test
    void findByEmail_isCaseInsensitive() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        this.pendingRegistrationRepository.upsertCode("Mixed-Case@Example.com", "hash", now, now.plusMinutes(5));

        Optional<PendingRegistration> found = this.pendingRegistrationRepository.findByEmail("mixed-case@example.com");

        assertThat(found).isPresent();
    }

    @Test
    void incrementAttempt_incrementsCount() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        PendingRegistration inserted =
                this.pendingRegistrationRepository.upsertCode("pending-3@example.com", "hash", now, now.plusMinutes(5));

        this.pendingRegistrationRepository.incrementAttempt(inserted.id());
        this.pendingRegistrationRepository.incrementAttempt(inserted.id());

        PendingRegistration updated = this.pendingRegistrationRepository
                .findByEmail("pending-3@example.com")
                .orElseThrow();
        assertThat(updated.attemptCount()).isEqualTo(2);
    }

    @Test
    void markVerified_setsVerifiedAt() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        PendingRegistration inserted =
                this.pendingRegistrationRepository.upsertCode("pending-4@example.com", "hash", now, now.plusMinutes(5));

        this.pendingRegistrationRepository.markVerified(inserted.id(), now);

        PendingRegistration updated = this.pendingRegistrationRepository
                .findByEmail("pending-4@example.com")
                .orElseThrow();
        assertThat(updated.verifiedAt()).isEqualTo(now);
    }

    @Test
    void deleteByEmail_removesRow() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        this.pendingRegistrationRepository.upsertCode("pending-5@example.com", "hash", now, now.plusMinutes(5));

        this.pendingRegistrationRepository.deleteByEmail("pending-5@example.com");

        assertThat(this.pendingRegistrationRepository.findByEmail("pending-5@example.com"))
                .isEmpty();
    }
}
