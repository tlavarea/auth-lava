package com.lava.swexpedited.batch.gfm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@WireMockTest
class GfmEcaPkiAuthenticatorTest {

    @Test
    void authenticate_clicksEcaPkiButtonThenSubmitsSamlAssertion(WireMockRuntimeInfo wireMockRuntimeInfo) {
        String baseUrl = wireMockRuntimeInfo.getHttpBaseUrl();

        stubFor(get(urlPathEqualTo("/oauth2/authorize"))
                .willReturn(aResponse().withStatus(200).withBody(loginSelectionPage(baseUrl))));
        stubFor(get(urlPathEqualTo("/pool/sso/authenticate/ext/64"))
                .withQueryParam("authenticity_token", equalTo("test-authenticity-token"))
                .withQueryParam("response_type", equalTo("code"))
                .withQueryParam("scope", equalTo("openid"))
                .withQueryParam("client_id", equalTo("mps-kmis-eca.transport.mil"))
                .withQueryParam("state", equalTo("test-state,OIDC"))
                .withQueryParam("redirect_uri", equalTo("https://mps-kmis.transport.mil/commonauth"))
                .withQueryParam("button", equalTo(""))
                .willReturn(aResponse().withStatus(200).withBody(samlAutoSubmitPage(baseUrl))));
        stubFor(post(urlPathEqualTo("/pool/sso/authenticate/ext/64"))
                .withQueryParam("verify-query-preserved", equalTo("yes"))
                .willReturn(aResponse().withStatus(200)));

        GfmEcaPkiAuthenticator authenticator = new GfmEcaPkiAuthenticator(RestClient.create(), Duration.ofMillis(10));

        authenticator.authenticate(URI.create(baseUrl + "/oauth2/authorize"));

        verify(1, getRequestedFor(urlPathEqualTo("/pool/sso/authenticate/ext/64")));
        verify(
                1,
                postRequestedFor(urlPathEqualTo("/pool/sso/authenticate/ext/64"))
                        .withRequestBody(containing("SAMLResponse=fake-saml-response"))
                        .withRequestBody(containing("authenticity_token=test-saml-token")));
    }

    @Test
    void authenticate_noLoginFormOnPage_throws(WireMockRuntimeInfo wireMockRuntimeInfo) {
        String baseUrl = wireMockRuntimeInfo.getHttpBaseUrl();
        stubFor(get(urlPathEqualTo("/oauth2/authorize"))
                .willReturn(aResponse().withStatus(200).withBody("<html><body>no form here</body></html>")));

        GfmEcaPkiAuthenticator authenticator = new GfmEcaPkiAuthenticator(RestClient.create(), Duration.ofMillis(10));

        assertThatThrownBy(() -> authenticator.authenticate(URI.create(baseUrl + "/oauth2/authorize")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("login form not found");
    }

    @Test
    void authenticate_noEcaPkiButtonOnPage_throws(WireMockRuntimeInfo wireMockRuntimeInfo) {
        String baseUrl = wireMockRuntimeInfo.getHttpBaseUrl();
        stubFor(get(urlPathEqualTo("/oauth2/authorize"))
                .willReturn(aResponse().withStatus(200).withBody("""
                                <html><body>
                                <form id="login_form" method="post" action="/pool/sso/authenticate/lp/15">
                                <button name="button" type="submit" formaction="/pool/sso/authenticate/ext/28" formmethod="get">Passkey</button>
                                </form>
                                </body></html>
                                """)));

        GfmEcaPkiAuthenticator authenticator = new GfmEcaPkiAuthenticator(RestClient.create(), Duration.ofMillis(10));

        assertThatThrownBy(() -> authenticator.authenticate(URI.create(baseUrl + "/oauth2/authorize")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ECA PKI login button not found");
    }

    /** Trimmed but structurally faithful to the real GFM SSO login-selection page (captured via HAR). */
    private String loginSelectionPage(String baseUrl) {
        return """
                <html><body>
                <form id="login_form" method="post" action="/pool/sso/authenticate/lp/15?ignored=1">
                <input type="hidden" name="authenticity_token" value="test-authenticity-token" />
                <input type="hidden" name="sso_session[orig_url]" value="https%%3A%%2F%%2Fmps-kmis-eca.transport.mil" />
                <input type="hidden" name="sso_session[orig_method]" value="" />
                <input type="hidden" name="sso_session[renewed_session]" value="" />
                <input type="hidden" name="sso_session[pki_upgrade]" value="" />
                <input type="hidden" name="response_type" value="code" />
                <input type="hidden" name="scope" value="openid" />
                <input type="hidden" name="client_id" value="mps-kmis-eca.transport.mil" />
                <input type="hidden" name="state" value="test-state,OIDC" />
                <input type="hidden" name="redirect_uri" value="https://mps-kmis.transport.mil/commonauth" />
                <div id="login-buttons">
                <button name="button" type="submit" formaction="%s/pool/sso/authenticate/ext/28?ignored=1" formmethod="get">Passkey</button>
                <button name="button" type="submit" formaction="%s/pool/sso/authenticate/ext/64?ignored=1" formmethod="get">ECA PKI</button>
                </div>
                </form>
                </body></html>
                """.formatted(baseUrl, baseUrl);
    }

    /** Trimmed but structurally faithful to the real SAML auto-submit page GFM's ECA-PKI IdP returns. */
    private String samlAutoSubmitPage(String baseUrl) {
        return """
                <html><body onload="document.forms[0].submit()">
                <form method="post" action="%s/pool/sso/authenticate/ext/64?verify-query-preserved=yes">
                <input type="hidden" name="authenticity_token" value="test-saml-token" />
                <input type="hidden" name="SAMLResponse" value="fake-saml-response" />
                </form>
                </body></html>
                """.formatted(baseUrl);
    }
}
