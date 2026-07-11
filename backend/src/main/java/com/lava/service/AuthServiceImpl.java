package com.lava.service;

import com.lava.exception.InvalidRefreshTokenException;
import com.lava.logging.LogSanitizer;
import com.lava.model.auth.Issued;
import com.lava.model.auth.TokenPair;
import com.lava.model.auth.TokenPairBuilder;
import com.lava.model.database.tables.pojos.RefreshToken;
import com.lava.model.database.view.AuthUserView;
import com.lava.model.throttle.AuthThrottleScope;
import com.lava.repository.UserRepository;
import com.lava.security.AuthUserPrincipal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final MfaService mfaService;
    private final PasswordEncoder passwordEncoder;
    private final RateLimitService rateLimitService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public String completeMfaVerification(AuthUserPrincipal principal, String rawRefreshToken, String code) {
        this.mfaService.verifyCode(principal.getUserId(), code);
        this.refreshTokenService.markMfaVerified(rawRefreshToken);

        // principal here is reconstructed from the password-only-factor JWT's claims, so its
        // authority set already carries MFA_ENROLLED/FACTOR_PASSWORD markers alongside the real
        // role/permission authorities. Re-deriving a clean principal from the DB (same as
        // refresh()) avoids baking those markers into the fresh token a second time.
        AuthUserView freshUser = this.userRepository
                .findAuthUserById(principal.getUserId())
                .filter(user -> "active".equals(user.status()))
                .orElseThrow(InvalidRefreshTokenException::new);
        return this.jwtService.generateAccessToken(AuthUserPrincipal.from(freshUser), true, true);
    }

    @Override
    @Transactional
    public TokenPair login(String email, String rawPassword) {
        this.rateLimitService.checkNotLocked(AuthThrottleScope.LOGIN, email);

        Authentication authResult;
        try {
            authResult = this.authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, rawPassword));
        } catch (AuthenticationException e) {
            this.rateLimitService.recordFailure(AuthThrottleScope.LOGIN, email);
            throw e;
        }
        this.rateLimitService.recordSuccess(AuthThrottleScope.LOGIN, email);

        AuthUserPrincipal principal = (AuthUserPrincipal) authResult.getPrincipal();
        this.userRepository.recordLogin(principal.getUserId());
        boolean mfaEnrolled = this.mfaService.isEnrolled(principal.getUserId());
        String accessToken = this.jwtService.generateAccessToken(principal, mfaEnrolled, false);
        Issued refresh = this.refreshTokenService.issue(principal.getUserId());

        return TokenPairBuilder.builder()
                .accessToken(accessToken)
                .expiresInSeconds(this.jwtService.getAccessTokenTtlSeconds())
                .mfaEnrolled(mfaEnrolled)
                .principal(principal)
                .refreshToken(refresh.rawToken())
                .build();
    }

    @Override
    @Transactional
    public void logout(AuthUserPrincipal principal, Optional<String> rawRefreshToken) {
        if (rawRefreshToken.isPresent()) {
            this.refreshTokenService
                    .findForLogout(rawRefreshToken.get())
                    .filter(token -> token.userId().equals(principal.getUserId()))
                    .ifPresent(token -> this.refreshTokenService.revoke(token.id()));
        } else {
            this.refreshTokenService.revokeAllForUser(principal.getUserId());
        }

        log.info("logout::userId: {}", LogSanitizer.sanitize(principal.getUserId()));
    }

    @Override
    @Transactional
    public TokenPair refresh(String rawRefreshToken) {
        RefreshToken current = this.refreshTokenService.validateForRotation(rawRefreshToken);
        AuthUserView freshUser = this.userRepository
                .findAuthUserById(current.userId())
                .filter(user -> "active".equals(user.status()))
                .orElseThrow(InvalidRefreshTokenException::new);
        AuthUserPrincipal principal = AuthUserPrincipal.from(freshUser);
        boolean mfaEnrolled = this.mfaService.isEnrolled(principal.getUserId());
        String accessToken =
                this.jwtService.generateAccessToken(principal, mfaEnrolled, Boolean.TRUE.equals(current.mfaVerified()));
        Issued rotated = this.refreshTokenService.rotate(current);

        return TokenPairBuilder.builder()
                .accessToken(accessToken)
                .expiresInSeconds(this.jwtService.getAccessTokenTtlSeconds())
                .mfaEnrolled(mfaEnrolled)
                .principal(principal)
                .refreshToken(rotated.rawToken())
                .build();
    }
}
