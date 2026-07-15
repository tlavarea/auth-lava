package com.lava.swexpedited.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.boot.autoconfigure.app.GfmProperties;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClient;

class GfmHttpConfigurationTest {

    private final GfmHttpConfiguration gfmHttpConfiguration = new GfmHttpConfiguration();

    @Test
    void gfmRestClient_withValidKeystoreAndTruststore_buildsClient() throws Exception {
        GfmProperties gfmProperties = new GfmProperties(
                new ClassPathResource("gfm/test-keystore.p12"),
                "changeit",
                new ClassPathResource("gfm/test-truststore.jks"),
                "changeit",
                "test-client",
                "https://eta-teams.transport.mil",
                "https://mps-kmis.transport.mil",
                "https://gfm.transport.mil");

        RestClient restClient = this.gfmHttpConfiguration.gfmRestClient(
                gfmProperties,
                this.gfmHttpConfiguration.gfmCookieStore(),
                this.gfmHttpConfiguration.gfmGatewayReferer());

        assertThat(restClient).isNotNull();
    }

    @Test
    void logRequestAndCaptureGatewayReferer_gfmGatewayHopWithCookies_capturesFullRefererUrl() {
        AtomicReference<String> gfmGatewayReferer = new AtomicReference<>();
        BasicHttpRequest request = new BasicHttpRequest(
                "GET",
                URI.create(
                        "https://gfm.transport.mil/gfmgateway/GfmGateway?code=test-code&session_state=test-session"));
        request.addHeader("Cookie", "JSESSIONID=abc; gfm_session=def");

        GfmHttpConfiguration.logRequestAndCaptureGatewayReferer(
                request, "https://gfm.transport.mil", gfmGatewayReferer);

        assertThat(gfmGatewayReferer.get())
                .isEqualTo("https://gfm.transport.mil/gfmgateway/GfmGateway?code=test-code&session_state=test-session");
    }

    @Test
    void logRequestAndCaptureGatewayReferer_nonGatewayHopWithoutCookies_leavesRefererUnset() {
        AtomicReference<String> gfmGatewayReferer = new AtomicReference<>();
        BasicHttpRequest request = new BasicHttpRequest("POST", URI.create("https://gfm.transport.mil/atr/home"));

        GfmHttpConfiguration.logRequestAndCaptureGatewayReferer(
                request, "https://gfm.transport.mil", gfmGatewayReferer);

        assertThat(gfmGatewayReferer.get()).isNull();
    }
}
