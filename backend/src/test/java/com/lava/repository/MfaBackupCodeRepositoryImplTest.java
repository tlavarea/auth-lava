package com.lava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.model.database.tables.pojos.MfaBackupCode;
import com.lava.model.database.tables.pojos.User;
import java.time.LocalDateTime;
import java.util.List;
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
    void insertBatch_thenFindAllUnusedByUserId_roundTrips() {
        User user =
                this.userRepository.insert("backup-insert@example.com", "hash").orElseThrow();

        this.backupCodeRepository.insertBatch(user.id(), List.of("hash-1", "hash-2", "hash-3"));

        assertThat(this.backupCodeRepository.findAllUnusedByUserId(user.id()))
                .extracting(MfaBackupCode::codeHash)
                .containsExactlyInAnyOrder("hash-1", "hash-2", "hash-3");
    }

    @Test
    void markUsed_excludesCodeFromFindAllUnused() {
        User user =
                this.userRepository.insert("backup-used@example.com", "hash").orElseThrow();
        this.backupCodeRepository.insertBatch(user.id(), List.of("hash-used"));
        MfaBackupCode code =
                this.backupCodeRepository.findAllUnusedByUserId(user.id()).getFirst();

        this.backupCodeRepository.markUsed(code.id(), LocalDateTime.now());

        assertThat(this.backupCodeRepository.findAllUnusedByUserId(user.id())).isEmpty();
    }

    @Test
    void deleteAllByUserId_removesAllCodesForThatUser() {
        User userA = this.userRepository.insert("backup-a@example.com", "hash").orElseThrow();
        User userB = this.userRepository.insert("backup-b@example.com", "hash").orElseThrow();
        this.backupCodeRepository.insertBatch(userA.id(), List.of("a-hash-1", "a-hash-2"));
        this.backupCodeRepository.insertBatch(userB.id(), List.of("b-hash-1"));

        this.backupCodeRepository.deleteAllByUserId(userA.id());

        assertThat(this.backupCodeRepository.findAllUnusedByUserId(userA.id())).isEmpty();
        assertThat(this.backupCodeRepository.findAllUnusedByUserId(userB.id()))
                .extracting(MfaBackupCode::codeHash)
                .containsExactly("b-hash-1");
    }
}
