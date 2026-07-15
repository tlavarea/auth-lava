package com.lava.swexpedited.batch;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.lava.swexpedited.boot.autoconfigure.app.GfmProperties;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.web.client.RestClient;

@WireMockTest
class GfmLogoutJobListenerTest {

    @Test
    void afterJob_withXsrfTokenCookie_sendsBothLogoutRequestsWithCsrfToken(WireMockRuntimeInfo wireMockRuntimeInfo) {
        String baseUrl = wireMockRuntimeInfo.getHttpBaseUrl();
        stubFor(post(urlPathEqualTo("/gfmgateway/mod/logout"))
                .withQueryParam("CSRFToken", equalTo("test-csrf-value"))
                .willReturn(aResponse().withStatus(200)));
        stubFor(post(urlPathEqualTo("/oidc/logout")).willReturn(aResponse().withStatus(200)));
        CookieStore cookieStore = cookieStoreWithXsrfToken();

        GfmLogoutJobListener listener =
                new GfmLogoutJobListener(RestClient.create(), cookieStore, gfmProperties(baseUrl));

        assertThatCode(() -> listener.afterJob(mock(JobExecution.class))).doesNotThrowAnyException();

        verify(1, postRequestedFor(urlPathEqualTo("/gfmgateway/mod/logout")));
        verify(1, postRequestedFor(urlPathEqualTo("/oidc/logout")));
        assertThat(cookieStore.getCookies()).isEmpty();
    }

    @Test
    void afterJob_noXsrfTokenCookie_skipsGfmLogoutButAttemptsMps(WireMockRuntimeInfo wireMockRuntimeInfo) {
        String baseUrl = wireMockRuntimeInfo.getHttpBaseUrl();
        stubFor(post(urlPathEqualTo("/oidc/logout")).willReturn(aResponse().withStatus(200)));

        GfmLogoutJobListener listener =
                new GfmLogoutJobListener(RestClient.create(), new BasicCookieStore(), gfmProperties(baseUrl));

        assertThatCode(() -> listener.afterJob(mock(JobExecution.class))).doesNotThrowAnyException();

        verify(0, postRequestedFor(urlPathEqualTo("/gfmgateway/mod/logout")));
        verify(1, postRequestedFor(urlPathEqualTo("/oidc/logout")));
    }

    @Test
    void afterJob_gfmLogoutFails_stillAttemptsMpsLogoutAndDoesNotThrow(WireMockRuntimeInfo wireMockRuntimeInfo) {
        String baseUrl = wireMockRuntimeInfo.getHttpBaseUrl();
        stubFor(post(urlPathEqualTo("/gfmgateway/mod/logout"))
                .willReturn(aResponse().withStatus(500)));
        stubFor(post(urlPathEqualTo("/oidc/logout")).willReturn(aResponse().withStatus(200)));
        CookieStore cookieStore = cookieStoreWithXsrfToken();

        GfmLogoutJobListener listener =
                new GfmLogoutJobListener(RestClient.create(), cookieStore, gfmProperties(baseUrl));

        assertThatCode(() -> listener.afterJob(mock(JobExecution.class))).doesNotThrowAnyException();

        verify(1, postRequestedFor(urlPathEqualTo("/oidc/logout")));
        assertThat(cookieStore.getCookies()).isEmpty();
    }

    @Test
    void afterJob_mpsLogoutFails_doesNotThrow(WireMockRuntimeInfo wireMockRuntimeInfo) {
        String baseUrl = wireMockRuntimeInfo.getHttpBaseUrl();
        stubFor(post(urlPathEqualTo("/gfmgateway/mod/logout"))
                .willReturn(aResponse().withStatus(200)));
        stubFor(post(urlPathEqualTo("/oidc/logout")).willReturn(aResponse().withStatus(500)));
        CookieStore cookieStore = cookieStoreWithXsrfToken();

        GfmLogoutJobListener listener =
                new GfmLogoutJobListener(RestClient.create(), cookieStore, gfmProperties(baseUrl));

        assertThatCode(() -> listener.afterJob(mock(JobExecution.class))).doesNotThrowAnyException();

        assertThat(cookieStore.getCookies()).isEmpty();
    }

    private CookieStore cookieStoreWithXsrfToken() {
        BasicCookieStore cookieStore = new BasicCookieStore();
        BasicClientCookie cookie = new BasicClientCookie("XSRF-TOKEN", "test-csrf-value");
        cookie.setDomain("gfm.transport.mil");
        cookie.setPath("/");
        cookieStore.addCookie(cookie);
        return cookieStore;
    }

    private GfmProperties gfmProperties(String baseUrl) {
        return new GfmProperties(null, null, null, null, "test-client", baseUrl, baseUrl, baseUrl);
    }
}
