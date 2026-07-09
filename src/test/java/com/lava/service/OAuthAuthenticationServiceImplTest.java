package com.lava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lava.exception.InvalidOAuthUserStateException;
import com.lava.exception.UnverifiedOAuthEmailException;
import com.lava.model.auth.Issued;
import com.lava.model.auth.IssuedBuilder;
import com.lava.model.auth.TokenPair;
import com.lava.model.database.tables.pojos.OauthAccount;
import com.lava.model.database.tables.pojos.User;
import com.lava.model.database.view.AuthUserView;
import com.lava.model.database.view.AuthUserViewBuilder;
import com.lava.repository.OauthAccountRepository;
import com.lava.repository.UserRepository;
import com.lava.security.oauth.OAuthIdentity;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OAuthAuthenticationServiceImplTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private MfaService mfaService;

    @Mock
    private OauthAccountRepository oauthAccountRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserRepository userRepository;

    private OAuthAuthenticationServiceImpl service;

    @BeforeEach
    void setUp() {
        this.service = new OAuthAuthenticationServiceImpl(
                this.jwtService,
                this.mfaService,
                this.oauthAccountRepository,
                this.refreshTokenService,
                this.userRepository);
    }

    @Test
    void authenticate_newUserViaGoogle_createsUserAndLinksAccount() {
        OAuthIdentity identity = new OAuthIdentity("google", "g-123", "new@example.com", true);

        when(this.oauthAccountRepository.findByProviderAndProviderUserId("google", "g-123"))
                .thenReturn(Optional.empty());
        when(this.userRepository.findAuthUserByEmail("new@example.com")).thenReturn(Optional.empty());
        when(this.userRepository.insertVerifiedFromOAuth("new@example.com")).thenReturn(Optional.of(user(42L)));
        when(this.userRepository.findAuthUserById(42L)).thenReturn(Optional.of(authUserView(42L, "active")));
        this.stubTokenIssuance();

        TokenPair pair = this.service.authenticate(identity);

        assertThat(pair.principal().getUserId()).isEqualTo(42L);
        verify(this.userRepository).insertVerifiedFromOAuth("new@example.com");
        verify(this.oauthAccountRepository).insert(42L, "google", "g-123");
    }

    @Test
    void authenticate_verifiedEmailMatchesExistingAccount_linksWithoutCreatingNewUser() {
        OAuthIdentity identity = new OAuthIdentity("google", "g-999", "existing@example.com", true);

        when(this.oauthAccountRepository.findByProviderAndProviderUserId("google", "g-999"))
                .thenReturn(Optional.empty());
        when(this.userRepository.findAuthUserByEmail("existing@example.com"))
                .thenReturn(Optional.of(authUserView(7L, "active")));
        when(this.userRepository.findAuthUserById(7L)).thenReturn(Optional.of(authUserView(7L, "active")));
        this.stubTokenIssuance();

        TokenPair pair = this.service.authenticate(identity);

        assertThat(pair.principal().getUserId()).isEqualTo(7L);
        verify(this.userRepository, never()).insertVerifiedFromOAuth(anyString());
        verify(this.oauthAccountRepository).insert(7L, "google", "g-999");
    }

    @Test
    void authenticate_alreadyLinkedAccount_skipsLookupsAndIssuesTokens() {
        OAuthIdentity identity = new OAuthIdentity("github", "gh-1", "someone@example.com", true);

        when(this.oauthAccountRepository.findByProviderAndProviderUserId("github", "gh-1"))
                .thenReturn(Optional.of(oauthAccount(3L)));
        when(this.userRepository.findAuthUserById(3L)).thenReturn(Optional.of(authUserView(3L, "active")));
        this.stubTokenIssuance();

        TokenPair pair = this.service.authenticate(identity);

        assertThat(pair.principal().getUserId()).isEqualTo(3L);
        verify(this.userRepository, never()).findAuthUserByEmail(anyString());
        verify(this.userRepository, never()).insertVerifiedFromOAuth(anyString());
        verify(this.oauthAccountRepository, never()).insert(any(), anyString(), anyString());
    }

    @Test
    void authenticate_mfaEnrolledUser_generatesAccessTokenWithMfaEnrolledFlag() {
        OAuthIdentity identity = new OAuthIdentity("github", "gh-1", "someone@example.com", true);

        when(this.oauthAccountRepository.findByProviderAndProviderUserId("github", "gh-1"))
                .thenReturn(Optional.of(oauthAccount(3L)));
        when(this.userRepository.findAuthUserById(3L)).thenReturn(Optional.of(authUserView(3L, "active")));
        when(this.mfaService.isEnrolled(3L)).thenReturn(true);
        when(this.jwtService.generateAccessToken(any(), eq(true), eq(false))).thenReturn("access-token");
        when(this.jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(this.refreshTokenService.issue(any())).thenAnswer(invocation -> issued(invocation.getArgument(0)));

        this.service.authenticate(identity);

        verify(this.jwtService).generateAccessToken(any(), eq(true), eq(false));
    }

    @Test
    void authenticate_suspendedUser_isRejected() {
        OAuthIdentity identity = new OAuthIdentity("github", "gh-2", "suspended@example.com", true);

        when(this.oauthAccountRepository.findByProviderAndProviderUserId("github", "gh-2"))
                .thenReturn(Optional.of(oauthAccount(9L)));
        when(this.userRepository.findAuthUserById(9L)).thenReturn(Optional.of(authUserView(9L, "suspended")));

        assertThatThrownBy(() -> this.service.authenticate(identity))
                .isInstanceOf(InvalidOAuthUserStateException.class);
    }

    @Test
    void authenticate_unverifiedEmailWithNoExistingLink_isRejectedWithoutTouchingUserTable() {
        OAuthIdentity identity = new OAuthIdentity("github", "gh-3", "unverified@example.com", false);

        when(this.oauthAccountRepository.findByProviderAndProviderUserId("github", "gh-3"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.authenticate(identity)).isInstanceOf(UnverifiedOAuthEmailException.class);

        verify(this.userRepository, never()).findAuthUserByEmail(anyString());
        verify(this.userRepository, never()).insertVerifiedFromOAuth(anyString());
    }

    private void stubTokenIssuance() {
        when(this.mfaService.isEnrolled(any())).thenReturn(false);
        when(this.jwtService.generateAccessToken(any(), eq(false), eq(false))).thenReturn("access-token");
        when(this.jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(this.refreshTokenService.issue(any())).thenAnswer(invocation -> issued(invocation.getArgument(0)));
    }

    private static User user(Long id) {
        return new User(id, "x@example.com", null, true, "active", null, null, null);
    }

    private static OauthAccount oauthAccount(Long userId) {
        return new OauthAccount(1L, userId, "github", "gh-x", null, null, null);
    }

    private static AuthUserView authUserView(Long id, String status) {
        return AuthUserViewBuilder.builder()
                .id(id)
                .email("x@example.com")
                .emailVerified(true)
                .status(status)
                .roles(Set.of())
                .permissions(Set.of())
                .build();
    }

    private static Issued issued(Long userId) {
        return IssuedBuilder.builder()
                .rawToken("raw-refresh-token")
                .id(1L)
                .userId(userId)
                .build();
    }
}
