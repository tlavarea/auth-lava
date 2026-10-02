package com.lava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.boot.autoconfigure.app.JwtProperties;
import com.lava.model.auth.Issued;
import com.lava.model.database.tables.pojos.RefreshToken;
import com.lava.model.database.tables.pojos.User;
import com.lava.security.Hasher;
import com.lava.service.RefreshTokenServiceImpl;
import java.security.SecureRandom;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reuse grace against Postgres: a refresh whose response was lost - the client still holds the token that was
 * rotated away, and never saw its replacement - is rotated again instead of revoking every session, and the replacement
 * it never received is retired.
 */
@Transactional
class RefreshTokenGraceIntegrationTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void aLostResponse_isRotatedAgain_andTheReplacementItNeverReceivedIsRetired() {
        RefreshTokenServiceImpl tokens = new RefreshTokenServiceImpl(
                new JwtProperties(
                        "private-key",
                        "public-key",
                        "key-id",
                        "issuer",
                        Duration.ofMinutes(15),
                        Duration.ofDays(30),
                        Duration.ofSeconds(60)),
                this.refreshTokenRepository,
                new SecureRandom());
        User user = this.userRepository.insert("grace@example.com", "hash").orElseThrow();
        Issued held = tokens.issue(user.id());

        // The refresh the client sent; the server rotated, and the response never arrived.
        Issued lost = tokens.rotate(tokens.validateForRotation(held.rawToken()));

        // The client tries again with the only token it has.
        RefreshToken again = tokens.validateForRotation(held.rawToken());
        Issued fresh = tokens.rotate(again);

        RefreshToken neverReceived = this.refreshTokenRepository
                .findByTokenHash(Hasher.hash(lost.rawToken()))
                .orElseThrow();
        RefreshToken current = this.refreshTokenRepository
                .findByTokenHash(Hasher.hash(fresh.rawToken()))
                .orElseThrow();
        RefreshToken original = this.refreshTokenRepository
                .findByTokenHash(Hasher.hash(held.rawToken()))
                .orElseThrow();
        assertThat(neverReceived.revokedAt())
                .as("the replacement it never received is retired")
                .isNotNull();
        assertThat(current.revokedAt()).as("the new token works").isNull();
        assertThat(original.replacedById()).isEqualTo(fresh.id());
    }
}
