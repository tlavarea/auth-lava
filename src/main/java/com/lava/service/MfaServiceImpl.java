package com.lava.service;

import com.lava.boot.autoconfigure.app.MfaProperties;
import com.lava.exception.InvalidTotpCodeException;
import com.lava.exception.MfaAlreadyEnabledException;
import com.lava.exception.MfaEnrollmentNotFoundException;
import com.lava.model.database.tables.pojos.MfaBackupCode;
import com.lava.model.database.tables.pojos.MfaMethod;
import com.lava.model.mfa.MfaMethodType;
import com.lava.model.mfa.TotpEnrollment;
import com.lava.model.mfa.TotpEnrollmentBuilder;
import com.lava.repository.MfaBackupCodeRepository;
import com.lava.repository.MfaMethodRepository;
import com.lava.security.AuthUserPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
@Transactional(readOnly = true)
public class MfaServiceImpl implements MfaService {

    private static final String BACKUP_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final MfaBackupCodeRepository backupCodeRepository;
    private final MfaMethodRepository mfaMethodRepository;
    private final MfaProperties mfaProperties;
    private final SecureRandom secureRandom;
    private final TextEncryptor totpSecretEncryptor;
    private final TotpService totpService;

    @Override
    @Transactional
    public List<String> confirmEnrollment(AuthUserPrincipal principal, Long mfaMethodId, String code) {
        MfaMethod method = this.findOwnedUnconfirmedMethod(principal.getUserId(), mfaMethodId);
        String secret = this.totpSecretEncryptor.decrypt(method.secretEncrypted());

        if (!this.totpService.verifyCode(secret, code)) {
            throw new InvalidTotpCodeException();
        }

        this.mfaMethodRepository.markVerifiedAndEnabled(method.id(), LocalDateTime.now());

        List<String> rawBackupCodes = this.generateBackupCodes();
        this.backupCodeRepository.deleteAllByUserId(principal.getUserId());
        this.backupCodeRepository.insertBatch(
                principal.getUserId(),
                rawBackupCodes.stream().map(MfaServiceImpl::hash).toList());

        log.info("confirmEnrollment::enabled TOTP for userId: {}", principal.getUserId());
        return rawBackupCodes;
    }

    @Override
    public boolean isEnrolled(Long userId) {
        return this.mfaMethodRepository
                .findEnabledByUserIdAndType(userId, MfaMethodType.TOTP.dbValue())
                .isPresent();
    }

    @Override
    @Transactional
    public TotpEnrollment startEnrollment(AuthUserPrincipal principal) {
        String type = MfaMethodType.TOTP.dbValue();

        if (this.mfaMethodRepository
                .findEnabledByUserIdAndType(principal.getUserId(), type)
                .isPresent()) {
            throw new MfaAlreadyEnabledException();
        }

        this.mfaMethodRepository.deleteUnconfirmedByUserIdAndType(principal.getUserId(), type);

        String secret = this.totpService.generateSecret();
        MfaMethod method = this.mfaMethodRepository.insertPending(
                principal.getUserId(), type, this.totpSecretEncryptor.encrypt(secret));

        return TotpEnrollmentBuilder.builder()
                .mfaMethodId(method.id())
                .otpAuthUri(this.totpService.buildOtpAuthUri(principal.getEmail(), secret))
                .qrCodeDataUri(this.totpService.generateQrCodeDataUri(principal.getEmail(), secret))
                .secret(secret)
                .build();
    }

    @Override
    @Transactional
    public void verifyCode(Long userId, String code) {
        Optional<MfaMethod> method =
                this.mfaMethodRepository.findEnabledByUserIdAndType(userId, MfaMethodType.TOTP.dbValue());

        if (method.isPresent()
                && this.totpService.verifyCode(
                        this.totpSecretEncryptor.decrypt(method.get().secretEncrypted()), code)) {
            return;
        }

        MfaBackupCode backupCode = this.backupCodeRepository
                .findUnusedByUserIdAndCodeHash(userId, hash(code))
                .orElseThrow(InvalidTotpCodeException::new);
        this.backupCodeRepository.markUsed(backupCode.id(), LocalDateTime.now());
    }

    /**
     * Generates a single backup code from a charset that excludes visually ambiguous characters (0/O, 1/I/L), since
     * these codes are meant to be manually typed by a user.
     *
     * @return a random backup code.
     */
    private String generateBackupCode() {
        StringBuilder code = new StringBuilder(this.mfaProperties.backupCodeLength());

        for (int i = 0; i < this.mfaProperties.backupCodeLength(); i++) {
            code.append(BACKUP_CODE_ALPHABET.charAt(this.secureRandom.nextInt(BACKUP_CODE_ALPHABET.length())));
        }

        return code.toString();
    }

    private List<String> generateBackupCodes() {
        return IntStream.range(0, this.mfaProperties.backupCodeCount())
                .mapToObj(_ -> this.generateBackupCode())
                .toList();
    }

    /**
     * Looks up an in-progress (not yet enabled) MFA method, verifying it belongs to the given user.
     *
     * @param userId - the id of the user attempting to confirm enrollment.
     * @param mfaMethodId - the id of the MFA method to confirm.
     * @return the pending {@link MfaMethod}.
     */
    private MfaMethod findOwnedUnconfirmedMethod(Long userId, Long mfaMethodId) {
        MfaMethod method =
                this.mfaMethodRepository.findOptionalById(mfaMethodId).orElseThrow(MfaEnrollmentNotFoundException::new);

        if (!method.userId().equals(userId) || Boolean.TRUE.equals(method.isEnabled())) {
            throw new MfaEnrollmentNotFoundException();
        }

        return method;
    }

    /**
     * Creates a new hashed backup code, consistent with {@code RefreshTokenServiceImpl}'s SHA-256-hex hashing of raw
     * refresh tokens.
     *
     * @param rawCode - the backup code to hash.
     * @return the code as a hashed value.
     */
    private static String hash(String rawCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawCode.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
