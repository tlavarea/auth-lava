package com.lava.swexpedited.batch;

import java.net.URI;
import java.time.Duration;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Completes GFM's "ECA PKI" login option: a SAML 2.0 / mutual-TLS federation handshake through federation.eams.army.mil
 * and eca.federation.eams.army.mil that a plain cert-authenticated call to mps-kmis.transport.mil/oauth2/authorize
 * alone doesn't satisfy - that call just lands on a login-method-selection page (confirmed against a real HAR capture
 * of the browser flow; scjj-gfm-app's ported chain either relied on GFM auth requirements that have since changed, or
 * never actually needed this hop for its account).
 *
 * <p>Two HTML pages have to be scraped and their forms "submitted" exactly as a browser would: the login-selection page
 * (find the "ECA PKI" button and submit its form) and the SAML auto-submit page it eventually leads to (submit the
 * signed assertion form). Everything in between - including the actual mTLS client-cert challenge at
 * eca.federation.eams.army.mil - is a plain HTTP redirect that {@code gfmRestClient}'s Apache HttpClient5 follows
 * automatically, so this class never needs to know federation.eams.army.mil or eca.federation.eams.army.mil by name;
 * both forms' action URLs are read directly out of the HTML, the same way a browser discovers them.
 *
 * <p>The two forms are submitted differently because HTML form semantics differ by method: a GET form submission
 * discards the {@code action}/{@code formaction} URL's own query string entirely and replaces it with the form's
 * serialized fields (confirmed by the real capture: the button's {@code formaction} carries a {@code u=} param that
 * never appears in the actual request), whereas a POST form submission preserves the action URL's query string as-is
 * and puts the fields in the request body instead.
 */
@Component
@Slf4j
public class GfmEcaPkiAuthenticator {

    private final RestClient gfmRestClient;
    private final Duration retryBackoff;

    public GfmEcaPkiAuthenticator(
            @Qualifier("gfmRestClient") RestClient gfmRestClient,
            @Value("${gfm.retry-backoff:5s}") Duration retryBackoff) {
        this.gfmRestClient = gfmRestClient;
        this.retryBackoff = retryBackoff;
    }

    /**
     * @param mpsKmisAuthorizeUri the initial {@code mps-kmis.transport.mil/oauth2/authorize} URI - following its
     *     redirect chain lands on GFM's login-method-selection page.
     */
    public void authenticate(URI mpsKmisAuthorizeUri) {
        String loginSelectionPage = retrying(
                () -> gfmRestClient.get().uri(mpsKmisAuthorizeUri).retrieve().body(String.class));
        URI ecaPkiUri = ecaPkiButtonUri(loginSelectionPage);
        log.debug("authenticate::submitting ECA PKI login button");

        String samlAutoSubmitPage =
                retrying(() -> gfmRestClient.get().uri(ecaPkiUri).retrieve().body(String.class));
        submitSamlAssertion(samlAutoSubmitPage);
        log.debug("authenticate::SAML assertion submitted");
    }

    private URI ecaPkiButtonUri(String html) {
        Document doc = Jsoup.parse(html);
        Element form = doc.selectFirst("#login_form");
        if (form == null) {
            throw new IllegalStateException("GFM SSO login form not found - page structure may have changed");
        }

        Element ecaPkiButton = form.select("button").stream()
                .filter(button -> "ECA PKI".equals(button.text().trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ECA PKI login button not found on GFM SSO page"));

        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(ecaPkiButton.attr("formaction"))
                .replaceQuery(null);
        form.select("input[type=hidden]").forEach(input -> uri.queryParam(input.attr("name"), input.attr("value")));
        uri.queryParam(ecaPkiButton.attr("name"), ecaPkiButton.attr("value"));
        return uri.build().toUri();
    }

    private void submitSamlAssertion(String html) {
        Document doc = Jsoup.parse(html);
        Element form = doc.selectFirst("form");
        if (form == null) {
            throw new IllegalStateException(
                    "SAML assertion form not found - GFM ECA-PKI response page structure may have changed");
        }

        URI action = URI.create(form.attr("action"));
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        form.select("input[type=hidden]").forEach(input -> body.add(input.attr("name"), input.attr("value")));

        retrying(() -> gfmRestClient
                .post()
                .uri(action)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    private <T> T retrying(Supplier<T> call) {
        RetryTemplate retryTemplate = RetryTemplate.builder()
                .maxAttempts(4)
                .fixedBackoff(retryBackoff)
                .retryOn(HttpServerErrorException.class)
                .build();
        return retryTemplate.execute(context -> call.get());
    }
}
