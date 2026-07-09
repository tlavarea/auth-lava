package com.lava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.model.database.tables.pojos.MfaBackupCode;
import com.lava.model.database.tables.pojos.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class MfaBackupCodeRepositoryImplTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private MfaBackupCodeRepository backupCodeRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void insertBatch_thenFindUnusedByUserIdAndCodeHash_roundTrips() {
        User user =
                this.userRepository.insert("backup-insert@example.com", "hash").orElseThrow();

        this.backupCodeRepository.insertBatch(user.id(), List.of("hash-1", "hash-2", "hash-3"));

        assertThat(this.backupCodeRepository.findUnusedByUserIdAndCodeHash(user.id(), "hash-1"))
                .isPresent();
        assertThat(this.backupCodeRepository.findUnusedByUserIdAndCodeHash(user.id(), "hash-2"))
                .isPresent();
        assertThat(this.backupCodeRepository.findUnusedByUserIdAndCodeHash(user.id(), "missing"))
                .isEmpty();
    }

    @Test
    void markUsed_excludesCodeFromFindUnused() {
        User user =
                this.userRepository.insert("backup-used@example.com", "hash").orElseThrow();
        this.backupCodeRepository.insertBatch(user.id(), List.of("hash-used"));
        MfaBackupCode code = this.backupCodeRepository
                .findUnusedByUserIdAndCodeHash(user.id(), "hash-used")
                .orElseThrow();

        this.backupCodeRepository.markUsed(code.id(), LocalDateTime.now());

        Optional<MfaBackupCode> found = this.backupCodeRepository.findUnusedByUserIdAndCodeHash(user.id(), "hash-used");
        assertThat(found).isEmpty();
    }

    @Test
    void deleteAllByUserId_removesAllCodesForThatUser() {
        User userA = this.userRepository.insert("backup-a@example.com", "hash").orElseThrow();
        User userB = this.userRepository.insert("backup-b@example.com", "hash").orElseThrow();
        this.backupCodeRepository.insertBatch(userA.id(), List.of("a-hash-1", "a-hash-2"));
        this.backupCodeRepository.insertBatch(userB.id(), List.of("b-hash-1"));

        this.backupCodeRepository.deleteAllByUserId(userA.id());

        assertThat(this.backupCodeRepository.findUnusedByUserIdAndCodeHash(userA.id(), "a-hash-1"))
                .isEmpty();
        assertThat(this.backupCodeRepository.findUnusedByUserIdAndCodeHash(userB.id(), "b-hash-1"))
                .isPresent();
    }
}
