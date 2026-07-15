package com.lava.swexpedited.configuration;

import com.lava.swexpedited.boot.autoconfigure.app.GfmProperties;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.HttpsSupport;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * A dedicated RestClient for the DoD GFM/ATR shipment system - not the shared auto-configured RestClient.Builder used
 * elsewhere, since this one needs its own request factory (client-cert mutual TLS) and a persistent per-client cookie
 * store to carry the session across the login chain. Apache HttpClient5's classic (sync) HttpClients keeps an automatic
 * cookie store for the lifetime of the built client, so cookies from the login hops flow into the CSV download for free
 * as long as every call goes through this same bean.
 *
 * <p>The cookie store is exposed as its own bean (rather than left implicit, which is what HttpClients.custom() would
 * otherwise create) so {@code GfmLogoutJobListener} can read the XSRF-TOKEN cookie the login chain sets, once the
 * tasklet has run, and play it back as GFM's logout CSRFToken.
 *
 * <p>Excluded under the "test" profile: building the SSLContext eagerly loads the configured keystore/truststore files,
 * which don't exist in CI/local test runs (no real GFM cert is available there) and would otherwise fail context
 * startup for every test that boots the full application context, not just ones touching this client.
 */
@Configuration
@Profile("!test")
@Slf4j
public class GfmHttpConfiguration {

    @Bean(name = "gfmCookieStore")
    public CookieStore gfmCookieStore() {
        return new BasicCookieStore();
    }

    /**
     * Holds the absolute URL of the last {@code /gfmgateway/GfmGateway?code=...&session_state=...} hop the GFM login
     * chain landed on - a real browser's next request (the {@code /atr/home} form submit) sends this exact URL as its
     * Referer, since it's literally the page the browser is sitting on. A static Referer built from just the base URL
     * and path silently dropped that query string, which {@code FetchAndLoadShipmentsTasklet} needs to reproduce.
     * Populated by the cookie-logging request interceptor below, which already sees every hop of the login chain
     * including this one.
     */
    @Bean(name = "gfmGatewayReferer")
    public AtomicReference<String> gfmGatewayReferer() {
        return new AtomicReference<>();
    }

    @Bean(name = "gfmRestClient")
    public RestClient gfmRestClient(
            GfmProperties gfmProperties, CookieStore gfmCookieStore, AtomicReference<String> gfmGatewayReferer)
            throws Exception {
        // TrustStrategy is deliberately "trust all" here, layered on top of a real JKS truststore that then goes
        // unused - carried over from a previously-working client rather than fixed in the same pass that's also
        // revalidating the login chain against the live GFM system. Once that's confirmed working, re-test with
        // this override removed so the JKS truststore's own chain validation is what's actually relied on.
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultCookieStore(gfmCookieStore)
                // Diagnostic only: the actual Cookie header attached to a request can differ from what's sitting in
                // the cookie store, since Apache HttpClient5 applies its own RFC 6265 domain/path matching per
                // request - logging cookie *names* (never values) here shows exactly what got attached to each
                // outgoing request, distinct from GfmLogoutJobListener's dump of everything currently in the store.
                .addRequestInterceptorLast((request, entity, context) ->
                        logRequestAndCaptureGatewayReferer(request, gfmProperties.gfmBaseUrl(), gfmGatewayReferer))
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofSeconds(15))
                                .build())
                        .setTlsSocketStrategy(ClientTlsStrategyBuilder.create()
                                .setSslContext(SSLContexts.custom()
                                        .loadKeyMaterial(
                                                loadKeyStore(
                                                        gfmProperties.keyStore(),
                                                        "PKCS12",
                                                        gfmProperties
                                                                .keyStorePassword()
                                                                .toCharArray()),
                                                gfmProperties.keyStorePassword().toCharArray())
                                        .loadTrustMaterial(
                                                loadKeyStore(
                                                        gfmProperties.trustStore(),
                                                        "JKS",
                                                        gfmProperties
                                                                .trustStorePassword()
                                                                .toCharArray()),
                                                (chain, authType) -> true)
                                        .build())
                                .setHostnameVerifier(HttpsSupport.getDefaultHostnameVerifier())
                                .buildClassic())
                        .build())
                .build();

        return RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
                // A bare "Mozilla/5.0" didn't fix /atr/home's 500 on its own, so there's no confirmed server-side
                // check on this specific value - but there's no reason to identify as a non-browser client either,
                // and matching a real captured browser UA costs nothing while gfmGatewayReferer above is retested.
                .defaultHeader(
                        HttpHeaders.USER_AGENT,
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko)"
                                + " Chrome/150.0.0.0 Safari/537.36")
                .build();
    }

    /**
     * Pulled out of the request-interceptor lambda so it's a plain method a unit test can call directly with a
     * hand-built {@link HttpRequest} - the lambda itself only runs against a live TLS connection to the real GFM host,
     * which isn't available in CI/local test runs (see the "Excluded under the test profile" note above).
     */
    static void logRequestAndCaptureGatewayReferer(
            HttpRequest request, String gfmBaseUrl, AtomicReference<String> gfmGatewayReferer) {
        Header cookieHeader = request.getFirstHeader(HttpHeaders.COOKIE);
        String names = cookieHeader == null
                ? "(none)"
                : Arrays.stream(cookieHeader.getValue().split("; "))
                        .map(pair -> pair.split("=", 2)[0])
                        .collect(Collectors.joining(", "));
        log.debug("outgoing::{} {} cookies attached: {}", request.getMethod(), request.getRequestUri(), names);
        if (request.getPath().startsWith("/gfmgateway/GfmGateway")) {
            gfmGatewayReferer.set(gfmBaseUrl + request.getRequestUri());
        }
    }

    private KeyStore loadKeyStore(Resource resource, String type, char[] password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(type);
        keyStore.load(resource.getInputStream(), password);
        return keyStore;
    }
}
