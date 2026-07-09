package com.lava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lava.boot.autoconfigure.app.JwtProperties;
import com.lava.exception.InvalidRefreshTokenException;
import com.lava.model.auth.Issued;
import com.lava.model.database.tables.pojos.RefreshToken;
import com.lava.model.database.tables.pojos.RefreshTokenBuilder;
import com.lava.repository.RefreshTokenRepository;
import com.lava.security.Hasher;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties("secret", "issuer", Duration.ofMinutes(15), Duration.ofDays(30));
        this.service = new RefreshTokenServiceImpl(properties, this.refreshTokenRepository, new SecureRandom());
    }

    @Test
    void issue_hashesTheRawTokenBeforePersisting() {
        when(this.refreshTokenRepository.insert(eq(1L), any(), any(), eq(false)))
                .thenAnswer(invocation -> row(9L, 1L));

        Issued issued = this.service.issue(1L);

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(this.refreshTokenRepository).insert(eq(1L), hashCaptor.capture(), any(), eq(false));
        assertThat(hashCaptor.getValue()).isEqualTo(Hasher.hash(issued.rawToken()));
        assertThat(issued.id()).isEqualTo(9L);
        assertThat(issued.userId()).isEqualTo(1L);
    }

    @Test
    void markMfaVerified_delegatesToRepositoryWithHashedToken() {
        when(this.refreshTokenRepository.findByTokenHash(Hasher.hash("raw"))).thenReturn(Optional.of(row(9L, 1L)));

        this.service.markMfaVerified("raw");

        verify(this.refreshTokenRepository).markMfaVerified(9L);
    }

    @Test
    void markMfaVerified_tokenNotFound_throwsInvalidRefreshTokenException() {
        when(this.refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.markMfaVerified("raw")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void findForLogout_delegatesToRepositoryWithHashedToken() {
        when(this.refreshTokenRepository.findByTokenHash(Hasher.hash("raw"))).thenReturn(Optional.of(row(1L, 2L)));

        Optional<RefreshToken> found = this.service.findForLogout("raw");

        assertThat(found).isPresent();
        verify(this.refreshTokenRepository).findByTokenHash(Hasher.hash("raw"));
    }

    @Test
    void revoke_delegatesToRepository() {
        this.service.revoke(5L);

        verify(this.refreshTokenRepository).revoke(eq(5L), any(LocalDateTime.class));
    }

    @Test
    void revokeAllForUser_delegatesToRepository() {
        this.service.revokeAllForUser(7L);

        verify(this.refreshTokenRepository).revokeAllForUser(eq(7L), any(LocalDateTime.class));
    }

    @Test
    void rotate_issuesNewTokenAndRevokesOldOne() {
        RefreshToken old = row(1L, 3L);
        when(this.refreshTokenRepository.insert(eq(3L), any(), any(), eq(false)))
                .thenAnswer(invocation -> row(2L, 3L));

        Issued next = this.service.rotate(old);

        assertThat(next.id()).isEqualTo(2L);
        verify(this.refreshTokenRepository).revokeAndReplace(eq(1L), eq(2L), any(LocalDateTime.class));
    }

    @Test
    void rotate_oldTokenWasMfaVerified_carriesFlagForwardToNewToken() {
        RefreshToken old = RefreshTokenBuilder.builder()
                .id(1L)
                .userId(3L)
                .tokenHash("hash")
                .expiresAt(LocalDateTime.now().plusDays(30))
                .mfaVerified(true)
                .build();
        when(this.refreshTokenRepository.insert(eq(3L), any(), any(), eq(true))).thenAnswer(invocation -> row(2L, 3L));

        Issued next = this.service.rotate(old);

        assertThat(next.id()).isEqualTo(2L);
        verify(this.refreshTokenRepository).insert(eq(3L), any(), any(), eq(true));
    }

    @Test
    void validateForRotation_validToken_returnsRow() {
        RefreshToken valid = RefreshTokenBuilder.builder()
                .id(1L)
                .userId(3L)
                .tokenHash(Hasher.hash("raw"))
                .expiresAt(LocalDateTime.now().plusDays(1))
                .revokedAt(null)
                .build();
        when(this.refreshTokenRepository.findByTokenHash(Hasher.hash("raw"))).thenReturn(Optional.of(valid));

        RefreshToken result = this.service.validateForRotation("raw");

        assertThat(result).isEqualTo(valid);
    }

    @Test
    void validateForRotation_tokenNotFound_throwsInvalidRefreshTokenException() {
        when(this.refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.validateForRotation("raw"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void validateForRotation_reuseOfRevokedToken_revokesAllSessionsAndThrows() {
        RefreshToken revoked = RefreshTokenBuilder.builder()
                .id(1L)
                .userId(3L)
                .tokenHash(Hasher.hash("raw"))
                .expiresAt(LocalDateTime.now().plusDays(1))
                .revokedAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(this.refreshTokenRepository.findByTokenHash(Hasher.hash("raw"))).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> this.service.validateForRotation("raw"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(this.refreshTokenRepository, times(1)).revokeAllForUser(eq(3L), any(LocalDateTime.class));
    }

    @Test
    void validateForRotation_expiredButNotRevoked_throwsWithoutRevokingAllSessions() {
        RefreshToken expired = RefreshTokenBuilder.builder()
                .id(1L)
                .userId(3L)
                .tokenHash(Hasher.hash("raw"))
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .revokedAt(null)
                .build();
        when(this.refreshTokenRepository.findByTokenHash(Hasher.hash("raw"))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> this.service.validateForRotation("raw"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(this.refreshTokenRepository, never()).revokeAllForUser(anyLong(), any(LocalDateTime.class));
    }

    private static RefreshToken row(Long id, Long userId) {
        return RefreshTokenBuilder.builder()
                .id(id)
                .userId(userId)
                .tokenHash("hash")
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
    }
}
