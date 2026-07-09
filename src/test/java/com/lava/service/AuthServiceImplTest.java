package com.lava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lava.exception.InvalidRefreshTokenException;
import com.lava.model.auth.Issued;
import com.lava.model.auth.IssuedBuilder;
import com.lava.model.auth.TokenPair;
import com.lava.model.database.tables.pojos.RefreshToken;
import com.lava.model.database.view.AuthUserView;
import com.lava.model.database.view.AuthUserViewBuilder;
import com.lava.repository.UserRepository;
import com.lava.security.AuthUserPrincipal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private MfaService mfaService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserRepository userRepository;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        this.service = new AuthServiceImpl(
                this.authenticationManager,
                this.jwtService,
                this.mfaService,
                this.passwordEncoder,
                this.refreshTokenService,
                this.userRepository);
    }

    @Test
    void completeMfaVerification_success_verifiesCodeMarksRefreshTokenAndReturnsNewAccessTokenForFreshPrincipal() {
        // The injected principal is JWT-claim-derived and already carries MFA_ENROLLED/FACTOR_*
        // markers from the password-only-factor token; the service must re-derive a clean
        // principal from the DB (like refresh() does) rather than reuse those stale authorities.
        AuthUserPrincipal staleFromJwt = principal(1L, "active");
        when(this.userRepository.findAuthUserById(1L)).thenReturn(Optional.of(authUserView(1L, "active")));
        when(this.jwtService.generateAccessToken(any(), eq(true), eq(true))).thenReturn("upgraded-access-token");

        String accessToken = this.service.completeMfaVerification(staleFromJwt, "raw-refresh", "123456");

        assertThat(accessToken).isEqualTo("upgraded-access-token");
        verify(this.mfaService).verifyCode(1L, "123456");
        verify(this.refreshTokenService).markMfaVerified("raw-refresh");
        verify(this.jwtService).generateAccessToken(argThat(p -> !p.equals(staleFromJwt)), eq(true), eq(true));
    }

    @Test
    void completeMfaVerification_userNotFound_throwsInvalidRefreshTokenException() {
        AuthUserPrincipal principal = principal(1L, "active");
        when(this.userRepository.findAuthUserById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.completeMfaVerification(principal, "raw-refresh", "123456"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void login_success_returnsTokenPairFromAuthenticatedPrincipal() {
        AuthUserPrincipal principal = principal(1L, "active");
        Authentication authResult = new TestingAuthenticationToken(principal, null);
        when(this.authenticationManager.authenticate(any())).thenReturn(authResult);
        when(this.mfaService.isEnrolled(1L)).thenReturn(false);
        when(this.jwtService.generateAccessToken(principal, false, false)).thenReturn("access-token");
        when(this.jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(this.refreshTokenService.issue(1L)).thenReturn(issued(1L, "raw-refresh"));

        TokenPair pair = this.service.login("user@example.com", "password");

        assertThat(pair.accessToken()).isEqualTo("access-token");
        assertThat(pair.refreshToken()).isEqualTo("raw-refresh");
        assertThat(pair.expiresInSeconds()).isEqualTo(900L);
        assertThat(pair.principal()).isEqualTo(principal);
    }

    @Test
    void login_mfaEnrolledUser_generatesAccessTokenWithMfaEnrolledFlag() {
        AuthUserPrincipal principal = principal(1L, "active");
        Authentication authResult = new TestingAuthenticationToken(principal, null);
        when(this.authenticationManager.authenticate(any())).thenReturn(authResult);
        when(this.mfaService.isEnrolled(1L)).thenReturn(true);
        when(this.jwtService.generateAccessToken(principal, true, false)).thenReturn("access-token");
        when(this.jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(this.refreshTokenService.issue(1L)).thenReturn(issued(1L, "raw-refresh"));

        TokenPair pair = this.service.login("user@example.com", "password");

        assertThat(pair.accessToken()).isEqualTo("access-token");
        verify(this.jwtService).generateAccessToken(principal, true, false);
    }

    @Test
    void logout_tokenBelongsToPrincipal_revokesToken() {
        AuthUserPrincipal principal = principal(1L, "active");
        RefreshToken token = refreshTokenRow(10L, 1L);
        when(this.refreshTokenService.findForLogout("raw")).thenReturn(Optional.of(token));

        this.service.logout(principal, Optional.of("raw"));

        verify(this.refreshTokenService).revoke(10L);
        verify(this.refreshTokenService, never()).revokeAllForUser(anyLong());
    }

    @Test
    void logout_tokenBelongsToDifferentUser_doesNotRevoke() {
        AuthUserPrincipal principal = principal(1L, "active");
        RefreshToken tokenForOtherUser = refreshTokenRow(10L, 999L);
        when(this.refreshTokenService.findForLogout("raw")).thenReturn(Optional.of(tokenForOtherUser));

        this.service.logout(principal, Optional.of("raw"));

        verify(this.refreshTokenService, never()).revoke(anyLong());
        verify(this.refreshTokenService, never()).revokeAllForUser(anyLong());
    }

    @Test
    void logout_tokenNotFound_doesNotRevoke() {
        AuthUserPrincipal principal = principal(1L, "active");
        when(this.refreshTokenService.findForLogout("raw")).thenReturn(Optional.empty());

        this.service.logout(principal, Optional.of("raw"));

        verify(this.refreshTokenService, never()).revoke(anyLong());
        verify(this.refreshTokenService, never()).revokeAllForUser(anyLong());
    }

    @Test
    void logout_noRawRefreshToken_revokesAllSessionsForUser() {
        AuthUserPrincipal principal = principal(1L, "active");

        this.service.logout(principal, Optional.empty());

        verify(this.refreshTokenService).revokeAllForUser(1L);
        verify(this.refreshTokenService, never()).findForLogout(any());
    }

    @Test
    void refresh_success_returnsRotatedTokenPair() {
        RefreshToken current = refreshTokenRow(5L, 2L);
        when(this.refreshTokenService.validateForRotation("raw")).thenReturn(current);
        when(this.userRepository.findAuthUserById(2L)).thenReturn(Optional.of(authUserView(2L, "active")));
        when(this.mfaService.isEnrolled(2L)).thenReturn(false);
        when(this.jwtService.generateAccessToken(any(), eq(false), eq(false))).thenReturn("new-access-token");
        when(this.jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(this.refreshTokenService.rotate(current)).thenReturn(issued(2L, "new-refresh"));

        TokenPair pair = this.service.refresh("raw");

        assertThat(pair.accessToken()).isEqualTo("new-access-token");
        assertThat(pair.refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void refresh_mfaAlreadyVerifiedForSession_carriesTotpFactorForward() {
        RefreshToken current =
                new RefreshToken(5L, 2L, "hash", LocalDateTime.now().plusDays(30), null, null, null, true);
        when(this.refreshTokenService.validateForRotation("raw")).thenReturn(current);
        when(this.userRepository.findAuthUserById(2L)).thenReturn(Optional.of(authUserView(2L, "active")));
        when(this.mfaService.isEnrolled(2L)).thenReturn(true);
        when(this.jwtService.generateAccessToken(any(), eq(true), eq(true))).thenReturn("new-access-token");
        when(this.jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(this.refreshTokenService.rotate(current)).thenReturn(issued(2L, "new-refresh"));

        TokenPair pair = this.service.refresh("raw");

        assertThat(pair.accessToken()).isEqualTo("new-access-token");
        verify(this.jwtService).generateAccessToken(any(), eq(true), eq(true));
    }

    @Test
    void refresh_userNotFound_throwsInvalidRefreshTokenException() {
        RefreshToken current = refreshTokenRow(5L, 2L);
        when(this.refreshTokenService.validateForRotation("raw")).thenReturn(current);
        when(this.userRepository.findAuthUserById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.refresh("raw")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_userNotActive_throwsInvalidRefreshTokenException() {
        RefreshToken current = refreshTokenRow(5L, 2L);
        when(this.refreshTokenService.validateForRotation("raw")).thenReturn(current);
        when(this.userRepository.findAuthUserById(2L)).thenReturn(Optional.of(authUserView(2L, "suspended")));

        assertThatThrownBy(() -> this.service.refresh("raw")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    private static AuthUserPrincipal principal(Long userId, String status) {
        return AuthUserPrincipal.builder()
                .userId(userId)
                .email("user@example.com")
                .passwordHash("hash")
                .status(status)
                .emailVerified(true)
                .authorities(Set.of())
                .build();
    }

    private static AuthUserView authUserView(Long id, String status) {
        return AuthUserViewBuilder.builder()
                .id(id)
                .email("user@example.com")
                .passwordHash("hash")
                .status(status)
                .emailVerified(true)
                .roles(Set.of())
                .permissions(Set.of())
                .build();
    }

    private static RefreshToken refreshTokenRow(Long id, Long userId) {
        return new RefreshToken(id, userId, "hash", LocalDateTime.now().plusDays(30), null, null, null, null);
    }

    private static Issued issued(Long userId, String rawToken) {
        return IssuedBuilder.builder()
                .id(1L)
                .userId(userId)
                .rawToken(rawToken)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
    }
}
