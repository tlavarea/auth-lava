package com.lava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lava.boot.autoconfigure.app.EmailChangeProperties;
import com.lava.exception.InvalidVerificationCodeException;
import com.lava.exception.TooManyRequestsException;
import com.lava.model.database.tables.pojos.PendingEmailChange;
import com.lava.repository.PendingEmailChangeRepository;
import com.lava.repository.UserRepository;
import com.lava.security.Hasher;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailChangeServiceImplTest {

    @Mock
    private EmailService emailService;

    @Mock
    private PendingEmailChangeRepository pendingEmailChangeRepository;

    @Mock
    private UserRepository userRepository;

    private EmailChangeProperties emailChangeProperties;
    private EmailChangeServiceImpl service;

    @BeforeEach
    void setUp() {
        this.emailChangeProperties = new EmailChangeProperties(6, Duration.ofMinutes(5), 5, Duration.ofSeconds(60));
        this.service = new EmailChangeServiceImpl(
                this.emailChangeProperties,
                this.emailService,
                this.pendingEmailChangeRepository,
                new SecureRandom(),
                this.userRepository);
    }

    @Test
    void start_emailNotInUseAndNoExistingCode_generatesAndSendsCode() {
        when(this.userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(this.pendingEmailChangeRepository.findByUserId(1L)).thenReturn(Optional.empty());

        this.service.start(1L, "new@example.com");

        verify(this.pendingEmailChangeRepository).upsertCode(eq(1L), eq("new@example.com"), anyString(), any(), any());
        verify(this.emailService).sendVerificationCode(eq("new@example.com"), anyString());
    }

    @Test
    void start_emailAlreadyInUse_silentlyNoOpsWithoutSendingCode() {
        when(this.userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        this.service.start(1L, "taken@example.com");

        verify(this.emailService, never()).sendVerificationCode(any(), any());
        verify(this.pendingEmailChangeRepository, never()).upsertCode(any(), any(), any(), any(), any());
    }

    @Test
    void start_recentCodeAlreadyPending_throwsTooManyRequestsAndDoesNotSend() {
        when(this.userRepository.existsByEmail("cooldown@example.com")).thenReturn(false);
        PendingEmailChange recent = pendingRow(1L, 1L, "cooldown@example.com", "hash", LocalDateTime.now(), 0);
        when(this.pendingEmailChangeRepository.findByUserId(1L)).thenReturn(Optional.of(recent));

        assertThatThrownBy(() -> this.service.start(1L, "cooldown@example.com"))
                .isInstanceOf(TooManyRequestsException.class);

        verify(this.emailService, never()).sendVerificationCode(any(), any());
    }

    @Test
    void start_staleCodeExistsPastCooldown_generatesAndSendsNewCode() {
        when(this.userRepository.existsByEmail("stale@example.com")).thenReturn(false);
        PendingEmailChange stale = pendingRow(
                1L, 1L, "stale@example.com", "hash", LocalDateTime.now().minusMinutes(10), 0);
        when(this.pendingEmailChangeRepository.findByUserId(1L)).thenReturn(Optional.of(stale));

        this.service.start(1L, "stale@example.com");

        verify(this.emailService).sendVerificationCode(eq("stale@example.com"), anyString());
    }

    @Test
    void confirm_correctCode_updatesEmailAndDeletesPendingRow() {
        String code = "123456";
        PendingEmailChange pending =
                pendingRow(1L, 1L, "verified@example.com", Hasher.hash(code), LocalDateTime.now(), 0);
        when(this.pendingEmailChangeRepository.findByUserId(1L)).thenReturn(Optional.of(pending));

        String newEmail = this.service.confirm(1L, code);

        assertThat(newEmail).isEqualTo("verified@example.com");
        verify(this.userRepository).updateEmail(1L, "verified@example.com");
        verify(this.pendingEmailChangeRepository).deleteByUserId(1L);
    }

    @Test
    void confirm_wrongCode_incrementsAttemptAndThrowsWithoutUpdating() {
        PendingEmailChange pending =
                pendingRow(1L, 1L, "target@example.com", Hasher.hash("111111"), LocalDateTime.now(), 0);
        when(this.pendingEmailChangeRepository.findByUserId(1L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> this.service.confirm(1L, "222222"))
                .isInstanceOf(InvalidVerificationCodeException.class);

        verify(this.pendingEmailChangeRepository).incrementAttempt(1L);
        verify(this.userRepository, never()).updateEmail(any(), any());
    }

    @Test
    void confirm_expiredCode_throwsWithoutIncrementingAttempt() {
        PendingEmailChange pending = pendingRow(
                1L,
                1L,
                "target@example.com",
                Hasher.hash("123456"),
                LocalDateTime.now().minusMinutes(10),
                0);
        when(this.pendingEmailChangeRepository.findByUserId(1L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> this.service.confirm(1L, "123456"))
                .isInstanceOf(InvalidVerificationCodeException.class);

        verify(this.pendingEmailChangeRepository, never()).incrementAttempt(any());
    }

    @Test
    void confirm_noPendingChange_throwsInvalidVerificationCode() {
        when(this.pendingEmailChangeRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.confirm(1L, "123456"))
                .isInstanceOf(InvalidVerificationCodeException.class);
    }

    @Test
    void confirm_maxAttemptsExceeded_throwsTooManyRequests() {
        PendingEmailChange pending =
                pendingRow(1L, 1L, "target@example.com", Hasher.hash("123456"), LocalDateTime.now(), 5);
        when(this.pendingEmailChangeRepository.findByUserId(1L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> this.service.confirm(1L, "123456")).isInstanceOf(TooManyRequestsException.class);

        verify(this.pendingEmailChangeRepository, never()).incrementAttempt(any());
    }

    private static PendingEmailChange pendingRow(
            Long id, Long userId, String newEmail, String codeHash, LocalDateTime createdAt, int attemptCount) {
        return new PendingEmailChange(
                id, userId, newEmail, codeHash, createdAt.plusMinutes(5), attemptCount, createdAt);
    }
}
