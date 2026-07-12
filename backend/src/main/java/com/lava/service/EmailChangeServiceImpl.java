package com.lava.service;

import com.lava.boot.autoconfigure.app.EmailChangeProperties;
import com.lava.exception.InvalidVerificationCodeException;
import com.lava.exception.TooManyRequestsException;
import com.lava.logging.LogSanitizer;
import com.lava.model.database.tables.pojos.PendingEmailChange;
import com.lava.repository.PendingEmailChangeRepository;
import com.lava.repository.UserRepository;
import com.lava.security.Hasher;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
@Transactional(readOnly = true)
public class EmailChangeServiceImpl implements EmailChangeService {

    private final EmailChangeProperties emailChangeProperties;
    private final EmailService emailService;
    private final PendingEmailChangeRepository pendingEmailChangeRepository;
    private final SecureRandom secureRandom;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public String confirm(Long userId, String code) {
        PendingEmailChange pending = this.pendingEmailChangeRepository
                .findByUserId(userId)
                .orElseThrow(InvalidVerificationCodeException::new);

        if (pending.expiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidVerificationCodeException();
        }

        if (pending.attemptCount() >= this.emailChangeProperties.maxAttempts()) {
            throw new TooManyRequestsException("Too many incorrect attempts - request a new code");
        }

        if (!pending.codeHash().equals(Hasher.hash(code))) {
            this.pendingEmailChangeRepository.incrementAttempt(pending.id());
            throw new InvalidVerificationCodeException();
        }

        this.userRepository.updateEmail(userId, pending.newEmail());
        this.pendingEmailChangeRepository.deleteByUserId(userId);
        log.info("confirm::userId: {}", LogSanitizer.sanitize(userId));
        return pending.newEmail();
    }

    @Override
    @Transactional
    public void start(Long userId, String newEmail) {
        // Deliberately a silent no-op rather than an error: returning a distinguishable response
        // (or throwing) here would let this authenticated endpoint be used to enumerate which
        // emails already have an account, the same reasoning as RegistrationServiceImpl#start.
        if (this.userRepository.existsByEmail(newEmail)) {
            log.warn("start::skipped, email already in use: {}", LogSanitizer.sanitize(newEmail));
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        this.pendingEmailChangeRepository
                .findByUserId(userId)
                .filter(row -> row.createdAt().isAfter(now.minus(this.emailChangeProperties.resendCooldown())))
                .ifPresent(row -> {
                    throw new TooManyRequestsException("Please wait before requesting another code");
                });

        String code = this.generateCode();
        this.pendingEmailChangeRepository.upsertCode(
                userId, newEmail, Hasher.hash(code), now, now.plus(this.emailChangeProperties.codeTtl()));
        this.emailService.sendVerificationCode(newEmail, code);
        log.info(
                "start::code sent, userId: {}, newEmail: {}",
                LogSanitizer.sanitize(userId),
                LogSanitizer.sanitize(newEmail));
    }

    /**
     * Generates a random numeric code with however many digits {@code email-change.code-length} specifies, left-padded
     * with zeros.
     *
     * @return the plaintext code.
     */
    private String generateCode() {
        int length = this.emailChangeProperties.codeLength();
        int upperBoundExclusive = (int) Math.pow(10, length);
        int value = this.secureRandom.nextInt(upperBoundExclusive);
        return String.format("%0" + length + "d", value);
    }
}
