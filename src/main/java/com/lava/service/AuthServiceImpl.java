package com.lava.service;

import com.lava.exception.EmailAlreadyRegisteredException;
import com.lava.exception.InvalidRefreshTokenException;
import com.lava.logging.LogSanitizer;
import com.lava.model.auth.Issued;
import com.lava.model.auth.TokenPair;
import com.lava.model.auth.TokenPairBuilder;
import com.lava.model.database.tables.pojos.RefreshToken;
import com.lava.model.database.view.AuthUserView;
import com.lava.repository.UserRepository;
import com.lava.security.AuthUserPrincipal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public TokenPair login(String email, String rawPassword) {
        Authentication authResult = this.authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, rawPassword));
        AuthUserPrincipal principal = (AuthUserPrincipal) authResult.getPrincipal();
        String accessToken = this.jwtService.generateAccessToken(principal);
        Issued refresh = this.refreshTokenService.issue(principal.getUserId());

        return TokenPairBuilder.builder()
                .accessToken(accessToken)
                .expiresInSeconds(this.jwtService.getAccessTokenTtlSeconds())
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
        String accessToken = this.jwtService.generateAccessToken(principal);
        Issued rotated = this.refreshTokenService.rotate(current);

        return TokenPairBuilder.builder()
                .accessToken(accessToken)
                .expiresInSeconds(this.jwtService.getAccessTokenTtlSeconds())
                .principal(principal)
                .refreshToken(rotated.rawToken())
                .build();
    }

    @Override
    @Transactional
    public void register(String email, String rawPassword) {
        if (this.userRepository.existsByEmail(email)) {
            log.warn("register::email already exists: {}", LogSanitizer.sanitize(email));
            throw new EmailAlreadyRegisteredException(email);
        }

        this.userRepository.insert(email, this.passwordEncoder.encode(rawPassword));
        log.info("register::email registered: {}", LogSanitizer.sanitize(email));
    }
}
