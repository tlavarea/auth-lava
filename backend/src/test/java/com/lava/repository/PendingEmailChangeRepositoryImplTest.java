package com.lava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.model.database.tables.pojos.PendingEmailChange;
import com.lava.model.database.tables.pojos.User;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PendingEmailChangeRepositoryImplTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private PendingEmailChangeRepository pendingEmailChangeRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void upsertCode_noExistingRow_insertsFreshRow() {
        User user = this.userRepository.insert("owner-1@example.com", "hash").orElseThrow();
        LocalDateTime now = LocalDateTime.now().withNano(0);

        PendingEmailChange inserted = this.pendingEmailChangeRepository.upsertCode(
                user.id(), "new-1@example.com", "hash-1", now, now.plusMinutes(5));

        assertThat(inserted.userId()).isEqualTo(user.id());
        assertThat(inserted.newEmail()).isEqualTo("new-1@example.com");
        assertThat(inserted.codeHash()).isEqualTo("hash-1");
        assertThat(inserted.attemptCount()).isZero();
    }

    @Test
    void upsertCode_existingRow_overwritesCodeAndResetsAttempts() {
        User user = this.userRepository.insert("owner-2@example.com", "hash").orElseThrow();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        PendingEmailChange first = this.pendingEmailChangeRepository.upsertCode(
                user.id(), "old-target@example.com", "hash-old", now, now.plusMinutes(5));
        this.pendingEmailChangeRepository.incrementAttempt(first.id());

        PendingEmailChange second = this.pendingEmailChangeRepository.upsertCode(
                user.id(), "new-target@example.com", "hash-new", now.plusSeconds(1), now.plusMinutes(6));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.newEmail()).isEqualTo("new-target@example.com");
        assertThat(second.codeHash()).isEqualTo("hash-new");
        assertThat(second.attemptCount()).isZero();
    }

    @Test
    void upsertCode_normalizesNewEmailToLowercase() {
        User user = this.userRepository.insert("owner-3@example.com", "hash").orElseThrow();
        LocalDateTime now = LocalDateTime.now().withNano(0);

        PendingEmailChange inserted = this.pendingEmailChangeRepository.upsertCode(
                user.id(), "Mixed-Case@Example.com", "hash", now, now.plusMinutes(5));

        assertThat(inserted.newEmail()).isEqualTo("mixed-case@example.com");
    }

    @Test
    void findByUserId_notFound_isEmpty() {
        assertThat(this.pendingEmailChangeRepository.findByUserId(999999L)).isEmpty();
    }

    @Test
    void incrementAttempt_incrementsCount() {
        User user = this.userRepository.insert("owner-4@example.com", "hash").orElseThrow();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        PendingEmailChange inserted = this.pendingEmailChangeRepository.upsertCode(
                user.id(), "attempts@example.com", "hash", now, now.plusMinutes(5));

        this.pendingEmailChangeRepository.incrementAttempt(inserted.id());
        this.pendingEmailChangeRepository.incrementAttempt(inserted.id());

        PendingEmailChange updated =
                this.pendingEmailChangeRepository.findByUserId(user.id()).orElseThrow();
        assertThat(updated.attemptCount()).isEqualTo(2);
    }

    @Test
    void deleteByUserId_removesRow() {
        User user = this.userRepository.insert("owner-5@example.com", "hash").orElseThrow();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        this.pendingEmailChangeRepository.upsertCode(
                user.id(), "delete-me@example.com", "hash", now, now.plusMinutes(5));

        this.pendingEmailChangeRepository.deleteByUserId(user.id());

        assertThat(this.pendingEmailChangeRepository.findByUserId(user.id())).isEmpty();
    }

    @Test
    void findByUserId_afterUpsert_returnsRow() {
        User user = this.userRepository.insert("owner-6@example.com", "hash").orElseThrow();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        this.pendingEmailChangeRepository.upsertCode(user.id(), "findme@example.com", "hash", now, now.plusMinutes(5));

        Optional<PendingEmailChange> found = this.pendingEmailChangeRepository.findByUserId(user.id());

        assertThat(found).isPresent();
        assertThat(found.get().newEmail()).isEqualTo("findme@example.com");
    }
}
