package com.lava.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GithubEmailBackfillOAuth2UserServiceTest {

    private static final String EMAILS_JSON = """
            [
              {"email": "old@example.com", "primary": false, "verified": true},
              {"email": "hidden@example.com", "primary": true, "verified": false},
              {"email": "verified-primary@example.com", "primary": true, "verified": true}
            ]
            """;

    @Test
    void findVerifiedPrimaryEmail_picksTheVerifiedPrimaryAddress() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.github.com/user/emails"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-123"))
                .andRespond(withSuccess(EMAILS_JSON, MediaType.APPLICATION_JSON));

        GithubEmailBackfillOAuth2UserService service = new GithubEmailBackfillOAuth2UserService(builder);

        Optional<String> result = service.findVerifiedPrimaryEmail("token-123");

        assertThat(result).contains("verified-primary@example.com");
        server.verify();
    }

    @Test
    void findVerifiedPrimaryEmail_noneVerified_returnsEmpty() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.github.com/user/emails"))
                .andRespond(withSuccess("""
                        [{"email": "unverified@example.com", "primary": true, "verified": false}]
                        """, MediaType.APPLICATION_JSON));

        GithubEmailBackfillOAuth2UserService service = new GithubEmailBackfillOAuth2UserService(builder);

        assertThat(service.findVerifiedPrimaryEmail("token-123")).isEmpty();
    }

    @Test
    void findVerifiedPrimaryEmail_requestFails_returnsEmptyRatherThanThrowing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.github.com/user/emails")).andRespond(withServerError());

        GithubEmailBackfillOAuth2UserService service = new GithubEmailBackfillOAuth2UserService(builder);

        assertThat(service.findVerifiedPrimaryEmail("token-123")).isEmpty();
    }
}
