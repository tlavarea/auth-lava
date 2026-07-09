package com.lava.service;

import com.lava.exception.InvalidOAuthUserStateException;
import com.lava.exception.UnverifiedOAuthEmailException;
import com.lava.model.auth.Issued;
import com.lava.model.auth.TokenPair;
import com.lava.model.auth.TokenPairBuilder;
import com.lava.model.database.tables.pojos.OauthAccount;
import com.lava.model.database.tables.pojos.User;
import com.lava.model.database.view.AuthUserView;
import com.lava.repository.OauthAccountRepository;
import com.lava.repository.UserRepository;
import com.lava.security.AuthUserPrincipal;
import com.lava.security.oauth.OAuthIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class OAuthAuthenticationServiceImpl implements OAuthAuthenticationService {

    private final JwtService jwtService;
    private final MfaService mfaService;
    private final OauthAccountRepository oauthAccountRepository;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public TokenPair authenticate(OAuthIdentity identity) {
        Long userId = this.oauthAccountRepository
                .findByProviderAndProviderUserId(identity.provider(), identity.providerUserId())
                .map(OauthAccount::userId)
                .orElseGet(() -> this.linkOrCreateUser(identity));
        AuthUserView view = this.userRepository
                .findAuthUserById(userId)
                .filter(user -> "active".equals(user.status()))
                .orElseThrow(InvalidOAuthUserStateException::new);
        AuthUserPrincipal principal = AuthUserPrincipal.from(view);
        boolean mfaEnrolled = this.mfaService.isEnrolled(principal.getUserId());
        String accessToken = this.jwtService.generateAccessToken(principal, mfaEnrolled, false);
        Issued refresh = this.refreshTokenService.issue(principal.getUserId());

        return TokenPairBuilder.builder()
                .accessToken(accessToken)
                .expiresInSeconds(this.jwtService.getAccessTokenTtlSeconds())
                .principal(principal)
                .refreshToken(refresh.rawToken())
                .build();
    }

    /**
     * Resolves the user for a first-ever login from this provider account: links to an existing user with a matching,
     * provider-verified email, or creates a new one. Never links on an unverified email - that would let an attacker
     * take over an account by claiming someone else's address without controlling their inbox.
     *
     * @param identity - the identity reported by the provider.
     * @return the id of the user the oauth_account row was linked to.
     */
    private Long linkOrCreateUser(OAuthIdentity identity) {
        if (!identity.emailVerified()) {
            throw new UnverifiedOAuthEmailException();
        }

        Long userId = this.userRepository
                .findAuthUserByEmail(identity.email())
                .map(AuthUserView::id)
                .orElseGet(() -> this.userRepository
                        .insertVerifiedFromOAuth(identity.email())
                        .map(User::id)
                        .orElseThrow(() -> new IllegalStateException("insertVerifiedFromOAuth did not return a row")));

        this.oauthAccountRepository.insert(userId, identity.provider(), identity.providerUserId());
        return userId;
    }
}
