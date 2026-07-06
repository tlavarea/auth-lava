package com.lava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.model.database.tables.pojos.RefreshToken;
import com.lava.model.database.tables.pojos.User;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RefreshTokenRepositoryImplTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void insert_thenFindByTokenHash_roundTrips() {
        User user = this.userRepository.insert("refresh-1@example.com", "hash").orElseThrow();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(30).withNano(0);

        RefreshToken inserted = this.refreshTokenRepository.insert(user.id(), "hash-value-1", expiresAt);

        Optional<RefreshToken> found = this.refreshTokenRepository.findByTokenHash("hash-value-1");
        assertThat(found).contains(inserted);
        assertThat(found.get().userId()).isEqualTo(user.id());
        assertThat(found.get().revokedAt()).isNull();
    }

    @Test
    void revoke_setsRevokedAt() {
        User user = this.userRepository.insert("refresh-2@example.com", "hash").orElseThrow();
        RefreshToken inserted = this.refreshTokenRepository.insert(
                user.id(), "hash-value-2", LocalDateTime.now().plusDays(30));

        this.refreshTokenRepository.revoke(inserted.id(), LocalDateTime.now());

        RefreshToken updated =
                this.refreshTokenRepository.findByTokenHash("hash-value-2").orElseThrow();
        assertThat(updated.revokedAt()).isNotNull();
    }

    @Test
    void revokeAllForUser_onlyRevokesNonRevokedRowsForThatUser() {
        User userA = this.userRepository.insert("refresh-a@example.com", "hash").orElseThrow();
        User userB = this.userRepository.insert("refresh-b@example.com", "hash").orElseThrow();
        RefreshToken tokenA1 = this.refreshTokenRepository.insert(
                userA.id(), "hash-a1", LocalDateTime.now().plusDays(30));
        RefreshToken tokenA2 = this.refreshTokenRepository.insert(
                userA.id(), "hash-a2", LocalDateTime.now().plusDays(30));
        RefreshToken tokenB = this.refreshTokenRepository.insert(
                userB.id(), "hash-b1", LocalDateTime.now().plusDays(30));
        this.refreshTokenRepository.revoke(tokenA1.id(), LocalDateTime.now());

        // revokeAllForUser runs in its own REQUIRES_NEW transaction, so the setup above must be
        // committed first for that separate transaction to see these rows.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        this.refreshTokenRepository.revokeAllForUser(userA.id(), LocalDateTime.now());

        assertThat(this.refreshTokenRepository
                        .findByTokenHash("hash-a2")
                        .orElseThrow()
                        .revokedAt())
                .isNotNull();
        assertThat(this.refreshTokenRepository
                        .findByTokenHash("hash-b1")
                        .orElseThrow()
                        .revokedAt())
                .isNull();
    }

    @Test
    void revokeAndReplace_setsRevokedAtAndReplacedById() {
        User user = this.userRepository.insert("refresh-3@example.com", "hash").orElseThrow();
        RefreshToken oldToken = this.refreshTokenRepository.insert(
                user.id(), "hash-old", LocalDateTime.now().plusDays(30));
        RefreshToken newToken = this.refreshTokenRepository.insert(
                user.id(), "hash-new", LocalDateTime.now().plusDays(30));

        this.refreshTokenRepository.revokeAndReplace(oldToken.id(), newToken.id(), LocalDateTime.now());

        RefreshToken updated =
                this.refreshTokenRepository.findByTokenHash("hash-old").orElseThrow();
        assertThat(updated.revokedAt()).isNotNull();
        assertThat(updated.replacedById()).isEqualTo(newToken.id());
    }
}
