package com.lava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lava.boot.autoconfigure.app.RegistrationProperties;
import com.lava.exception.EmailAlreadyRegisteredException;
import com.lava.exception.InvalidRegistrationTokenException;
import com.lava.exception.InvalidVerificationCodeException;
import com.lava.exception.TooManyRequestsException;
import com.lava.model.database.tables.pojos.PendingRegistration;
import com.lava.model.database.tables.pojos.PendingRegistrationBuilder;
import com.lava.repository.PendingRegistrationRepository;
import com.lava.repository.UserRepository;
import com.lava.security.Hasher;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplTest {

    @Mock
    private EmailService emailService;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PendingRegistrationRepository pendingRegistrationRepository;

    @Mock
    private UserRepository userRepository;

    private RegistrationProperties registrationProperties;
    private RegistrationServiceImpl service;

    @BeforeEach
    void setUp() {
        this.registrationProperties =
                new RegistrationProperties(6, Duration.ofMinutes(5), 5, Duration.ofSeconds(60), Duration.ofMinutes(5));
        this.service = new RegistrationServiceImpl(
                this.emailService,
                this.jwtService,
                this.passwordEncoder,
                this.pendingRegistrationRepository,
                this.registrationProperties,
                new SecureRandom(),
                this.userRepository);
    }

    @Test
    void start_emailNotRegisteredAndNoExistingCode_generatesAndSendsCode() {
        when(this.userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(this.pendingRegistrationRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

        this.service.start("new@example.com");

        verify(this.pendingRegistrationRepository).upsertCode(eq("new@example.com"), anyString(), any(), any());
        verify(this.emailService).sendVerificationCode(eq("new@example.com"), anyString());
    }

    @Test
    void start_emailAlreadyRegistered_throwsAndDoesNotSend() {
        when(this.userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> this.service.start("existing@example.com"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(this.emailService, never()).sendVerificationCode(any(), any());
    }

    @Test
    void start_recentCodeAlreadyPending_throwsTooManyRequestsAndDoesNotSend() {
        when(this.userRepository.existsByEmail("cooldown@example.com")).thenReturn(false);
        PendingRegistration recent = pendingRow(1L, "cooldown@example.com", "hash", LocalDateTime.now(), 0, null);
        when(this.pendingRegistrationRepository.findByEmail("cooldown@example.com"))
                .thenReturn(Optional.of(recent));

        assertThatThrownBy(() -> this.service.start("cooldown@example.com"))
                .isInstanceOf(TooManyRequestsException.class);

        verify(this.emailService, never()).sendVerificationCode(any(), any());
    }

    @Test
    void start_staleCodeExistsPastCooldown_generatesAndSendsNewCode() {
        when(this.userRepository.existsByEmail("stale@example.com")).thenReturn(false);
        PendingRegistration stale =
                pendingRow(1L, "stale@example.com", "hash", LocalDateTime.now().minusMinutes(10), 0, null);
        when(this.pendingRegistrationRepository.findByEmail("stale@example.com"))
                .thenReturn(Optional.of(stale));

        this.service.start("stale@example.com");

        verify(this.emailService).sendVerificationCode(eq("stale@example.com"), anyString());
    }

    @Test
    void verifyCode_correctCode_marksVerifiedAndReturnsBridgeToken() {
        LocalDateTime now = LocalDateTime.now();
        String code = "123456";
        PendingRegistration pending = pendingRow(1L, "verify@example.com", Hasher.hash(code), now, 0, null);
        when(this.pendingRegistrationRepository.findByEmail("verify@example.com"))
                .thenReturn(Optional.of(pending));
        when(this.jwtService.generateRegistrationToken(
                        "verify@example.com", this.registrationProperties.bridgeTokenTtl()))
                .thenReturn("bridge-token");

        String token = this.service.verifyCode("verify@example.com", code);

        assertThat(token).isEqualTo("bridge-token");
        verify(this.pendingRegistrationRepository).markVerified(eq(1L), any());
        verify(this.pendingRegistrationRepository, never()).incrementAttempt(any());
    }

    @Test
    void verifyCode_wrongCode_incrementsAttemptAndThrows() {
        LocalDateTime now = LocalDateTime.now();
        PendingRegistration pending = pendingRow(1L, "verify2@example.com", Hasher.hash("111111"), now, 0, null);
        when(this.pendingRegistrationRepository.findByEmail("verify2@example.com"))
                .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> this.service.verifyCode("verify2@example.com", "222222"))
                .isInstanceOf(InvalidVerificationCodeException.class);

        verify(this.pendingRegistrationRepository).incrementAttempt(1L);
    }

    @Test
    void verifyCode_expiredCode_throwsWithoutIncrementingAttempt() {
        PendingRegistration pending = pendingRow(
                1L,
                "verify3@example.com",
                Hasher.hash("123456"),
                LocalDateTime.now().minusMinutes(10),
                0,
                null);
        when(this.pendingRegistrationRepository.findByEmail("verify3@example.com"))
                .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> this.service.verifyCode("verify3@example.com", "123456"))
                .isInstanceOf(InvalidVerificationCodeException.class);

        verify(this.pendingRegistrationRepository, never()).incrementAttempt(any());
    }

    @Test
    void verifyCode_noPendingRegistration_throwsInvalidVerificationCode() {
        when(this.pendingRegistrationRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.verifyCode("missing@example.com", "123456"))
                .isInstanceOf(InvalidVerificationCodeException.class);
    }

    @Test
    void verifyCode_maxAttemptsExceeded_throwsTooManyRequests() {
        PendingRegistration pending =
                pendingRow(1L, "maxed@example.com", Hasher.hash("123456"), LocalDateTime.now(), 5, null);
        when(this.pendingRegistrationRepository.findByEmail("maxed@example.com"))
                .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> this.service.verifyCode("maxed@example.com", "123456"))
                .isInstanceOf(TooManyRequestsException.class);

        verify(this.pendingRegistrationRepository, never()).incrementAttempt(any());
    }

    @Test
    void complete_validTokenAndVerifiedRow_createsUserAndDeletesPendingRow() {
        Claims claims = Jwts.claims()
                .subject("complete@example.com")
                .add(Map.of(JwtService.REGISTRATION_TOKEN_PURPOSE_CLAIM, JwtService.REGISTRATION_TOKEN_PURPOSE))
                .build();
        when(this.jwtService.parseAndValidate("valid-token")).thenReturn(claims);
        PendingRegistration verified =
                pendingRow(1L, "complete@example.com", "hash", LocalDateTime.now(), 0, LocalDateTime.now());
        when(this.pendingRegistrationRepository.findByEmail("complete@example.com"))
                .thenReturn(Optional.of(verified));
        when(this.passwordEncoder.encode("password123")).thenReturn("encoded");

        this.service.complete("valid-token", "password123");

        verify(this.userRepository).insertVerified("complete@example.com", "encoded");
        verify(this.pendingRegistrationRepository).deleteByEmail("complete@example.com");
    }

    @Test
    void complete_wrongTokenPurpose_throwsInvalidRegistrationToken() {
        Claims claims = Jwts.claims()
                .subject("wrong-purpose@example.com")
                .add(Map.of(JwtService.REGISTRATION_TOKEN_PURPOSE_CLAIM, "not-registration"))
                .build();
        when(this.jwtService.parseAndValidate("bad-purpose-token")).thenReturn(claims);

        assertThatThrownBy(() -> this.service.complete("bad-purpose-token", "password123"))
                .isInstanceOf(InvalidRegistrationTokenException.class);

        verify(this.userRepository, never()).insertVerified(any(), any());
    }

    @Test
    void complete_tokenFailsToParse_throwsInvalidRegistrationToken() {
        when(this.jwtService.parseAndValidate("garbage")).thenThrow(new JwtException("bad token"));

        assertThatThrownBy(() -> this.service.complete("garbage", "password123"))
                .isInstanceOf(InvalidRegistrationTokenException.class);
    }

    @Test
    void complete_rowNotVerified_throwsInvalidRegistrationToken() {
        Claims claims = Jwts.claims()
                .subject("notverified@example.com")
                .add(Map.of(JwtService.REGISTRATION_TOKEN_PURPOSE_CLAIM, JwtService.REGISTRATION_TOKEN_PURPOSE))
                .build();
        when(this.jwtService.parseAndValidate("valid-token")).thenReturn(claims);
        PendingRegistration unverified =
                pendingRow(1L, "notverified@example.com", "hash", LocalDateTime.now(), 0, null);
        when(this.pendingRegistrationRepository.findByEmail("notverified@example.com"))
                .thenReturn(Optional.of(unverified));

        assertThatThrownBy(() -> this.service.complete("valid-token", "password123"))
                .isInstanceOf(InvalidRegistrationTokenException.class);

        verify(this.userRepository, never()).insertVerified(any(), any());
    }

    @Test
    void complete_rowNoLongerExists_throwsInvalidRegistrationToken() {
        Claims claims = Jwts.claims()
                .subject("gone@example.com")
                .add(Map.of(JwtService.REGISTRATION_TOKEN_PURPOSE_CLAIM, JwtService.REGISTRATION_TOKEN_PURPOSE))
                .build();
        when(this.jwtService.parseAndValidate("valid-token")).thenReturn(claims);
        when(this.pendingRegistrationRepository.findByEmail("gone@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.complete("valid-token", "password123"))
                .isInstanceOf(InvalidRegistrationTokenException.class);
    }

    private static PendingRegistration pendingRow(
            Long id,
            String email,
            String codeHash,
            LocalDateTime createdAt,
            int attemptCount,
            LocalDateTime verifiedAt) {
        return PendingRegistrationBuilder.builder()
                .id(id)
                .email(email)
                .codeHash(codeHash)
                .createdAt(createdAt)
                .expiresAt(createdAt.plusMinutes(5))
                .attemptCount(attemptCount)
                .verifiedAt(verifiedAt)
                .build();
    }
}
