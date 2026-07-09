package com.lava.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lava.boot.autoconfigure.app.CookieProperties;
import com.lava.boot.autoconfigure.app.CorsProperties;
import com.lava.boot.autoconfigure.app.JwtProperties;
import com.lava.boot.autoconfigure.app.OAuthProperties;
import com.lava.configuration.SecurityConfiguration;
import com.lava.exception.EmailAlreadyRegisteredException;
import com.lava.exception.InvalidRegistrationTokenException;
import com.lava.exception.InvalidVerificationCodeException;
import com.lava.exception.TooManyRequestsException;
import com.lava.security.oauth.GithubEmailBackfillOAuth2UserService;
import com.lava.service.JwtService;
import com.lava.service.RegistrationService;
import com.lava.web.AuthCookieFactory;
import com.lava.web.oauth.OAuthAuthenticationFailureHandler;
import com.lava.web.oauth.OAuthAuthenticationSuccessHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RegistrationController.class)
@Import(SecurityConfiguration.class)
@EnableConfigurationProperties({CookieProperties.class, CorsProperties.class, JwtProperties.class, OAuthProperties.class
})
@ActiveProfiles("test")
class RegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthCookieFactory cookieFactory;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private GithubEmailBackfillOAuth2UserService githubEmailBackfillOAuth2UserService;

    @MockitoBean
    private OAuthAuthenticationSuccessHandler oAuthAuthenticationSuccessHandler;

    @MockitoBean
    private OAuthAuthenticationFailureHandler oAuthAuthenticationFailureHandler;

    @Test
    void start_success_returns200() throws Exception {
        this.mockMvc
                .perform(post("/api/auth/register/start")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\"}"))
                .andExpect(status().isOk());

        verify(this.registrationService).start("new@example.com");
    }

    @Test
    void start_blankEmail_returns400() throws Exception {
        this.mockMvc
                .perform(post("/api/auth/register/start")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void start_emailAlreadyRegistered_returns409() throws Exception {
        doThrow(new EmailAlreadyRegisteredException("dup@example.com"))
                .when(this.registrationService)
                .start("dup@example.com");

        this.mockMvc
                .perform(post("/api/auth/register/start")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dup@example.com\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void start_tooManyRequests_returns429() throws Exception {
        doThrow(new TooManyRequestsException("Please wait before requesting another code"))
                .when(this.registrationService)
                .start("cooldown@example.com");

        this.mockMvc
                .perform(post("/api/auth/register/start")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cooldown@example.com\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void verifyCode_success_returnsRegistrationToken() throws Exception {
        when(this.registrationService.verifyCode("new@example.com", "123456")).thenReturn("bridge-token");

        this.mockMvc
                .perform(post("/api/auth/register/verify-code")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationToken").value("bridge-token"));
    }

    @Test
    void verifyCode_invalidCode_returns401() throws Exception {
        doThrow(new InvalidVerificationCodeException())
                .when(this.registrationService)
                .verifyCode(eq("new@example.com"), eq("000000"));

        this.mockMvc
                .perform(post("/api/auth/register/verify-code")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"code\":\"000000\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyCode_wrongLengthCode_returns400() throws Exception {
        this.mockMvc
                .perform(post("/api/auth/register/verify-code")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"code\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void complete_success_returns201() throws Exception {
        this.mockMvc
                .perform(post("/api/auth/register/complete")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationToken\":\"bridge-token\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());

        verify(this.registrationService).complete("bridge-token", "password123");
    }

    @Test
    void complete_invalidToken_returns401() throws Exception {
        doThrow(new InvalidRegistrationTokenException())
                .when(this.registrationService)
                .complete(eq("bad-token"), eq("password123"));

        this.mockMvc
                .perform(post("/api/auth/register/complete")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationToken\":\"bad-token\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void complete_passwordTooShort_returns400() throws Exception {
        this.mockMvc
                .perform(post("/api/auth/register/complete")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationToken\":\"bridge-token\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }
}
