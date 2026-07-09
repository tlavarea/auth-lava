package com.lava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lava.boot.autoconfigure.app.MfaProperties;
import com.lava.exception.InvalidTotpCodeException;
import com.lava.exception.MfaAlreadyEnabledException;
import com.lava.exception.MfaEnrollmentNotFoundException;
import com.lava.model.database.tables.pojos.MfaBackupCode;
import com.lava.model.database.tables.pojos.MfaBackupCodeBuilder;
import com.lava.model.database.tables.pojos.MfaMethod;
import com.lava.model.database.tables.pojos.MfaMethodBuilder;
import com.lava.model.mfa.TotpEnrollment;
import com.lava.repository.MfaBackupCodeRepository;
import com.lava.repository.MfaMethodRepository;
import com.lava.security.AuthUserPrincipal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.encrypt.TextEncryptor;

@ExtendWith(MockitoExtension.class)
class MfaServiceImplTest {

    @Mock
    private MfaBackupCodeRepository backupCodeRepository;

    @Mock
    private MfaMethodRepository mfaMethodRepository;

    @Mock
    private TextEncryptor totpSecretEncryptor;

    @Mock
    private TotpService totpService;

    private MfaServiceImpl service;

    @BeforeEach
    void setUp() {
        this.service = new MfaServiceImpl(
                this.backupCodeRepository,
                this.mfaMethodRepository,
                properties(),
                new SecureRandom(),
                this.totpSecretEncryptor,
                this.totpService);
    }

    @Test
    void startEnrollment_noExistingMethod_insertsPendingAndReturnsEnrollment() {
        AuthUserPrincipal principal = principal(1L);
        when(this.mfaMethodRepository.findEnabledByUserIdAndType(1L, "totp")).thenReturn(Optional.empty());
        when(this.totpService.generateSecret()).thenReturn("SECRET");
        when(this.totpSecretEncryptor.encrypt("SECRET")).thenReturn("encrypted-secret");
        when(this.mfaMethodRepository.insertPending(1L, "totp", "encrypted-secret"))
                .thenReturn(mfaMethod(10L, 1L, false));
        when(this.totpService.buildOtpAuthUri("user@example.com", "SECRET")).thenReturn("otpauth://totp/uri");
        when(this.totpService.generateQrCodeDataUri("user@example.com", "SECRET"))
                .thenReturn("data:image/png;base64,x");

        TotpEnrollment enrollment = this.service.startEnrollment(principal);

        assertThat(enrollment.mfaMethodId()).isEqualTo(10L);
        assertThat(enrollment.secret()).isEqualTo("SECRET");
        assertThat(enrollment.otpAuthUri()).isEqualTo("otpauth://totp/uri");
        assertThat(enrollment.qrCodeDataUri()).isEqualTo("data:image/png;base64,x");
        verify(this.mfaMethodRepository).deleteUnconfirmedByUserIdAndType(1L, "totp");
    }

    @Test
    void startEnrollment_alreadyEnabled_throwsMfaAlreadyEnabledException() {
        AuthUserPrincipal principal = principal(1L);
        when(this.mfaMethodRepository.findEnabledByUserIdAndType(1L, "totp"))
                .thenReturn(Optional.of(mfaMethod(5L, 1L, true)));

        assertThatThrownBy(() -> this.service.startEnrollment(principal))
                .isInstanceOf(MfaAlreadyEnabledException.class);

        verify(this.mfaMethodRepository, never()).insertPending(any(), any(), any());
    }

    @Test
    void confirmEnrollment_validCode_enablesMethodAndReturnsBackupCodes() {
        AuthUserPrincipal principal = principal(1L);
        MfaMethod pending = mfaMethod(10L, 1L, false);
        when(this.mfaMethodRepository.findOptionalById(10L)).thenReturn(Optional.of(pending));
        when(this.totpSecretEncryptor.decrypt("encrypted-secret")).thenReturn("SECRET");
        when(this.totpService.verifyCode("SECRET", "123456")).thenReturn(true);

        List<String> backupCodes = this.service.confirmEnrollment(principal, 10L, "123456");

        assertThat(backupCodes).hasSize(10);
        assertThat(backupCodes).doesNotHaveDuplicates();
        verify(this.mfaMethodRepository).markVerifiedAndEnabled(eq(10L), any(LocalDateTime.class));
        verify(this.backupCodeRepository).deleteAllByUserId(1L);
        verify(this.backupCodeRepository).insertBatch(eq(1L), anyList());
    }

    @Test
    void confirmEnrollment_invalidCode_throwsInvalidTotpCodeException() {
        AuthUserPrincipal principal = principal(1L);
        MfaMethod pending = mfaMethod(10L, 1L, false);
        when(this.mfaMethodRepository.findOptionalById(10L)).thenReturn(Optional.of(pending));
        when(this.totpSecretEncryptor.decrypt("encrypted-secret")).thenReturn("SECRET");
        when(this.totpService.verifyCode("SECRET", "000000")).thenReturn(false);

        assertThatThrownBy(() -> this.service.confirmEnrollment(principal, 10L, "000000"))
                .isInstanceOf(InvalidTotpCodeException.class);

        verify(this.mfaMethodRepository, never()).markVerifiedAndEnabled(any(), any());
    }

    @Test
    void confirmEnrollment_methodNotFound_throwsMfaEnrollmentNotFoundException() {
        AuthUserPrincipal principal = principal(1L);
        when(this.mfaMethodRepository.findOptionalById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.confirmEnrollment(principal, 99L, "123456"))
                .isInstanceOf(MfaEnrollmentNotFoundException.class);
    }

    @Test
    void confirmEnrollment_methodBelongsToDifferentUser_throwsMfaEnrollmentNotFoundException() {
        AuthUserPrincipal principal = principal(1L);
        when(this.mfaMethodRepository.findOptionalById(10L)).thenReturn(Optional.of(mfaMethod(10L, 999L, false)));

        assertThatThrownBy(() -> this.service.confirmEnrollment(principal, 10L, "123456"))
                .isInstanceOf(MfaEnrollmentNotFoundException.class);
    }

    @Test
    void confirmEnrollment_methodAlreadyEnabled_throwsMfaEnrollmentNotFoundException() {
        AuthUserPrincipal principal = principal(1L);
        when(this.mfaMethodRepository.findOptionalById(10L)).thenReturn(Optional.of(mfaMethod(10L, 1L, true)));

        assertThatThrownBy(() -> this.service.confirmEnrollment(principal, 10L, "123456"))
                .isInstanceOf(MfaEnrollmentNotFoundException.class);
    }

    @Test
    void isEnrolled_enabledMethodExists_returnsTrue() {
        when(this.mfaMethodRepository.findEnabledByUserIdAndType(1L, "totp"))
                .thenReturn(Optional.of(mfaMethod(5L, 1L, true)));

        assertThat(this.service.isEnrolled(1L)).isTrue();
    }

    @Test
    void isEnrolled_noEnabledMethod_returnsFalse() {
        when(this.mfaMethodRepository.findEnabledByUserIdAndType(1L, "totp")).thenReturn(Optional.empty());

        assertThat(this.service.isEnrolled(1L)).isFalse();
    }

    @Test
    void verifyCode_validTotpCode_succeedsWithoutTouchingBackupCodes() {
        when(this.mfaMethodRepository.findEnabledByUserIdAndType(1L, "totp"))
                .thenReturn(Optional.of(mfaMethod(5L, 1L, true)));
        when(this.totpSecretEncryptor.decrypt("encrypted-secret")).thenReturn("SECRET");
        when(this.totpService.verifyCode("SECRET", "123456")).thenReturn(true);

        this.service.verifyCode(1L, "123456");

        verify(this.backupCodeRepository, never()).findUnusedByUserIdAndCodeHash(any(), any());
    }

    @Test
    void verifyCode_invalidTotpButValidBackupCode_marksBackupCodeUsed() {
        when(this.mfaMethodRepository.findEnabledByUserIdAndType(1L, "totp"))
                .thenReturn(Optional.of(mfaMethod(5L, 1L, true)));
        when(this.totpSecretEncryptor.decrypt("encrypted-secret")).thenReturn("SECRET");
        when(this.totpService.verifyCode(eq("SECRET"), any())).thenReturn(false);
        when(this.backupCodeRepository.findUnusedByUserIdAndCodeHash(eq(1L), any()))
                .thenReturn(Optional.of(backupCode(20L)));

        this.service.verifyCode(1L, "BACKUP1234");

        verify(this.backupCodeRepository).markUsed(eq(20L), any(LocalDateTime.class));
    }

    @Test
    void verifyCode_noEnabledTotpMethod_fallsBackToBackupCodeDirectly() {
        when(this.mfaMethodRepository.findEnabledByUserIdAndType(1L, "totp")).thenReturn(Optional.empty());
        when(this.backupCodeRepository.findUnusedByUserIdAndCodeHash(eq(1L), any()))
                .thenReturn(Optional.of(backupCode(20L)));

        this.service.verifyCode(1L, "BACKUP1234");

        verify(this.backupCodeRepository).markUsed(eq(20L), any(LocalDateTime.class));
    }

    @Test
    void verifyCode_bothTotpAndBackupInvalid_throwsInvalidTotpCodeException() {
        when(this.mfaMethodRepository.findEnabledByUserIdAndType(1L, "totp"))
                .thenReturn(Optional.of(mfaMethod(5L, 1L, true)));
        when(this.totpSecretEncryptor.decrypt("encrypted-secret")).thenReturn("SECRET");
        when(this.totpService.verifyCode(eq("SECRET"), any())).thenReturn(false);
        when(this.backupCodeRepository.findUnusedByUserIdAndCodeHash(eq(1L), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.verifyCode(1L, "wrong")).isInstanceOf(InvalidTotpCodeException.class);

        verify(this.backupCodeRepository, never()).markUsed(any(), any());
    }

    private static AuthUserPrincipal principal(Long userId) {
        return AuthUserPrincipal.builder()
                .userId(userId)
                .email("user@example.com")
                .passwordHash("hash")
                .status("active")
                .emailVerified(true)
                .authorities(Set.of())
                .build();
    }

    private static MfaMethod mfaMethod(Long id, Long userId, boolean enabled) {
        return MfaMethodBuilder.builder()
                .id(id)
                .userId(userId)
                .type("totp")
                .secretEncrypted("encrypted-secret")
                .isEnabled(enabled)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private static MfaBackupCode backupCode(Long id) {
        return MfaBackupCodeBuilder.builder()
                .id(id)
                .userId(1L)
                .codeHash("hash")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private static MfaProperties properties() {
        return new MfaProperties("encryption-key", "5a1e2b3c4d5e6f708192a3b4c5d6e7f8", 10, 10, "auth-lava-test", 1);
    }
}
