package com.lava.web.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.boot.autoconfigure.app.OAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

class OAuthAuthenticationFailureHandlerTest {

    @Test
    void onAuthenticationFailure_redirectsToFailureUri() throws Exception {
        OAuthProperties properties =
                new OAuthProperties("http://localhost:4200/", "http://localhost:4200/login?error=oauth");
        OAuthAuthenticationFailureHandler handler = new OAuthAuthenticationFailureHandler(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("denied"));

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:4200/login?error=oauth");
    }
}
