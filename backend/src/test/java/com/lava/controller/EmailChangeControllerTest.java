package com.lava.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lava.boot.autoconfigure.app.CookieProperties;
import com.lava.boot.autoconfigure.app.CorsProperties;
import com.lava.boot.autoconfigure.app.JwtProperties;
import com.lava.boot.autoconfigure.app.OAuthProperties;
import com.lava.configuration.SecurityConfiguration;
import com.lava.exception.InvalidVerificationCodeException;
import com.lava.exception.TooManyRequestsException;
import com.lava.security.AuthUserPrincipal;
import com.lava.security.oauth.GithubEmailBackfillOAuth2UserService;
import com.lava.service.EmailChangeService;
import com.lava.service.JwtService;
import com.lava.web.AuthCookieFactory;
import com.lava.web.oauth.OAuthAuthenticationFailureHandler;
import com.lava.web.oauth.OAuthAuthenticationSuccessHandler;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EmailChangeController.class)
@Import(SecurityConfiguration.class)
@EnableConfigurationProperties({CookieProperties.class, CorsProperties.class, JwtProperties.class, OAuthProperties.class
})
@ActiveProfiles("test")
class EmailChangeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthCookieFactory cookieFactory;

    @MockitoBean
    private EmailChangeService emailChangeService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private GithubEmailBackfillOAuth2UserService githubEmailBackfillOAuth2UserService;

    @MockitoBean
    private OAuthAuthenticationSuccessHandler oAuthAuthenticationSuccessHandler;

    @MockitoBean
    private OAuthAuthenticationFailureHandler oAuthAuthenticationFailureHandler;

    @Test
    void start_unauthenticated_returns401() throws Exception {
        this.mockMvc
                .perform(post("/api/auth/email/change")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"new@example.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void start_validRequest_returns200AndCallsService() throws Exception {
        AuthUserPrincipal principal = principal(1L);

        this.mockMvc
                .perform(post("/api/auth/email/change")
                        .with(csrf())
                        .with(authentication(authToken(principal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"new@example.com\"}"))
                .andExpect(status().isOk());

        verify(this.emailChangeService).start(1L, "new@example.com");
    }

    @Test
    void start_blankEmail_returns400() throws Exception {
        AuthUserPrincipal principal = principal(1L);

        this.mockMvc
                .perform(post("/api/auth/email/change")
                        .with(csrf())
                        .with(authentication(authToken(principal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void start_invalidEmailFormat_returns400() throws Exception {
        AuthUserPrincipal principal = principal(1L);

        this.mockMvc
                .perform(post("/api/auth/email/change")
                        .with(csrf())
                        .with(authentication(authToken(principal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void start_tooManyRequests_returns429() throws Exception {
        AuthUserPrincipal principal = principal(1L);
        doThrow(new TooManyRequestsException("Please wait before requesting another code"))
                .when(this.emailChangeService)
                .start(1L, "new@example.com");

        this.mockMvc
                .perform(post("/api/auth/email/change")
                        .with(csrf())
                        .with(authentication(authToken(principal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"new@example.com\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void verify_validCode_returnsUpdatedUser() throws Exception {
        AuthUserPrincipal principal = principal(1L);
        when(this.emailChangeService.confirm(1L, "123456")).thenReturn("new@example.com");

        this.mockMvc
                .perform(post("/api/auth/email/change/verify")
                        .with(csrf())
                        .with(authentication(authToken(principal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void verify_invalidCode_returns401() throws Exception {
        AuthUserPrincipal principal = principal(1L);
        doThrow(new InvalidVerificationCodeException())
                .when(this.emailChangeService)
                .confirm(1L, "000000");

        this.mockMvc
                .perform(post("/api/auth/email/change/verify")
                        .with(csrf())
                        .with(authentication(authToken(principal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"000000\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verify_blankCode_returns400() throws Exception {
        AuthUserPrincipal principal = principal(1L);

        this.mockMvc
                .perform(post("/api/auth/email/change/verify")
                        .with(csrf())
                        .with(authentication(authToken(principal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    private static Authentication authToken(AuthUserPrincipal principal) {
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    private static AuthUserPrincipal principal(Long id) {
        return AuthUserPrincipal.builder()
                .userId(id)
                .email("user@example.com")
                .passwordHash("hash")
                .status("active")
                .emailVerified(true)
                .authorities(Set.of())
                .build();
    }
}
