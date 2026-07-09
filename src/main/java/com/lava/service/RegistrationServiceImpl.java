package com.lava.service;

import com.lava.boot.autoconfigure.app.RegistrationProperties;
import com.lava.exception.EmailAlreadyRegisteredException;
import com.lava.exception.InvalidRegistrationTokenException;
import com.lava.exception.InvalidVerificationCodeException;
import com.lava.exception.TooManyRequestsException;
import com.lava.logging.LogSanitizer;
import com.lava.model.database.tables.pojos.PendingRegistration;
import com.lava.repository.PendingRegistrationRepository;
import com.lava.repository.UserRepository;
import com.lava.security.Hasher;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
@Transactional(readOnly = true)
public class RegistrationServiceImpl implements RegistrationService {

    private final EmailService emailService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final RegistrationProperties registrationProperties;
    private final SecureRandom secureRandom;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void complete(String rawRegistrationToken, String rawPassword) {
        String email = this.validateRegistrationToken(rawRegistrationToken);

        // Re-checking the DB row (not just trusting the token's own signature/expiry) means a
        // fresh /register/start call for this email - which clears verified_at - invalidates an
        // older bridge token even if it hasn't technically expired yet.
        this.pendingRegistrationRepository
                .findByEmail(email)
                .filter(row -> row.verifiedAt() != null)
                .orElseThrow(InvalidRegistrationTokenException::new);

        this.userRepository.insertVerified(email, this.passwordEncoder.encode(rawPassword));
        this.pendingRegistrationRepository.deleteByEmail(email);
        log.info("complete::registered: {}", LogSanitizer.sanitize(email));
    }

    @Override
    @Transactional
    public void start(String email) {
        if (this.userRepository.existsByEmail(email)) {
            log.warn("start::email already registered: {}", LogSanitizer.sanitize(email));
            throw new EmailAlreadyRegisteredException(email);
        }

        LocalDateTime now = LocalDateTime.now();
        this.pendingRegistrationRepository
                .findByEmail(email)
                .filter(row -> row.createdAt().isAfter(now.minus(this.registrationProperties.resendCooldown())))
                .ifPresent(row -> {
                    throw new TooManyRequestsException("Please wait before requesting another code");
                });

        String code = this.generateCode();
        this.pendingRegistrationRepository.upsertCode(
                email, Hasher.hash(code), now, now.plus(this.registrationProperties.codeTtl()));
        this.emailService.sendVerificationCode(email, code);
        log.info("start::code sent: {}", LogSanitizer.sanitize(email));
    }

    @Override
    @Transactional
    public String verifyCode(String email, String code) {
        PendingRegistration pending = this.pendingRegistrationRepository
                .findByEmail(email)
                .orElseThrow(InvalidVerificationCodeException::new);

        if (pending.expiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidVerificationCodeException();
        }

        if (pending.attemptCount() >= this.registrationProperties.maxAttempts()) {
            throw new TooManyRequestsException("Too many incorrect attempts - request a new code");
        }

        if (!pending.codeHash().equals(Hasher.hash(code))) {
            this.pendingRegistrationRepository.incrementAttempt(pending.id());
            throw new InvalidVerificationCodeException();
        }

        this.pendingRegistrationRepository.markVerified(pending.id(), LocalDateTime.now());
        log.info("verifyCode::verified: {}", LogSanitizer.sanitize(email));
        return this.jwtService.generateRegistrationToken(email, this.registrationProperties.bridgeTokenTtl());
    }

    /**
     * Generates a random numeric code with however many digits {@code registration.code-length} specifies, left-padded
     * with zeros.
     *
     * @return the plaintext code.
     */
    private String generateCode() {
        int length = this.registrationProperties.codeLength();
        int upperBoundExclusive = (int) Math.pow(10, length);
        int value = this.secureRandom.nextInt(upperBoundExclusive);
        return String.format("%0" + length + "d", value);
    }

    private String validateRegistrationToken(String rawToken) {
        try {
            Claims claims = this.jwtService.parseAndValidate(rawToken);

            if (!JwtService.REGISTRATION_TOKEN_PURPOSE.equals(
                    claims.get(JwtService.REGISTRATION_TOKEN_PURPOSE_CLAIM))) {
                throw new InvalidRegistrationTokenException();
            }

            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidRegistrationTokenException();
        }
    }
}
