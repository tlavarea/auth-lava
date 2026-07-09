package com.lava.security.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lava.logging.LogSanitizer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * GitHub's default /user endpoint returns a null email for users who keep their email private, and even when non-null,
 * that value carries no verification guarantee. This service always fetches /user/emails for GitHub logins and only
 * stamps an "emailVerified" attribute when a verified primary email is actually found there.
 */
@Service
@Slf4j
public class GithubEmailBackfillOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final String GITHUB_REGISTRATION_ID = "github";
    private static final String GITHUB_EMAILS_URI = "https://api.github.com/user/emails";

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final RestClient restClient;

    public GithubEmailBackfillOAuth2UserService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User user = this.delegate.loadUser(userRequest);

        if (!GITHUB_REGISTRATION_ID.equals(userRequest.getClientRegistration().getRegistrationId())) {
            return user;
        }

        return this.findVerifiedPrimaryEmail(userRequest.getAccessToken().getTokenValue())
                .map(email -> mergeVerifiedEmail(user, email))
                .orElse(user);
    }

    private static OAuth2User mergeVerifiedEmail(OAuth2User user, String verifiedEmail) {
        Map<String, Object> attributes = new HashMap<>(user.getAttributes());
        attributes.put("email", verifiedEmail);
        attributes.put("emailVerified", true);

        return new DefaultOAuth2User(user.getAuthorities(), attributes, "id");
    }

    /**
     * Fetches GitHub's per-account email list and picks the verified primary address.
     *
     * @param accessToken - the access token issued for this login.
     * @return the verified primary email, or empty if none was found or the call failed.
     */
    Optional<String> findVerifiedPrimaryEmail(String accessToken) {
        try {
            List<GithubEmail> emails = this.restClient
                    .get()
                    .uri(GITHUB_EMAILS_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (emails == null) {
                return Optional.empty();
            }

            return emails.stream()
                    .filter(GithubEmail::verified)
                    .sorted(Comparator.comparing(GithubEmail::primary).reversed())
                    .map(GithubEmail::email)
                    .findFirst();
        } catch (RuntimeException e) {
            log.warn(
                    "findVerifiedPrimaryEmail::failed to fetch github user emails: {}",
                    LogSanitizer.sanitize(ExceptionUtils.getMessage(e)),
                    e);
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GithubEmail(String email, boolean primary, boolean verified) {}
}
