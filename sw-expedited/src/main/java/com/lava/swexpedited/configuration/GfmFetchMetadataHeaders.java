package com.lava.swexpedited.configuration;

import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * GFM/ATR sits behind a WAF that appears to key off Chrome's Fetch Metadata / Client Hint headers on every request, not
 * just full page navigations - proven by replaying a captured browser request with curl and bisecting it against an
 * otherwise-identical Java request that still 500'd. Real Chrome sends a different header shape depending on how the
 * request was made:
 *
 * <ul>
 *   <li>{@link #forDocumentNavigation} - a real page load or full-page form submit (GFM's own screen-navigation forms,
 *       e.g. {@code POST /atr/home}, {@code GET /atr/shipment}): {@code Sec-Fetch-Dest: document},
 *       {@code Sec-Fetch-Mode: navigate}, plus {@code Sec-Fetch-User}/{@code Upgrade-Insecure-Requests}/
 *       {@code Cache-Control}, which only ever appear on navigations.
 *   <li>{@link #forXhr} - an in-page fetch/XHR call (e.g. {@code GET /atr/getBid}): {@code Sec-Fetch-Dest: empty},
 *       {@code Sec-Fetch-Mode: cors}, a JSON {@code Accept}, and none of the navigation-only headers above.
 * </ul>
 *
 * <p>User-Agent isn't set here - {@code gfmRestClient}'s defaultHeader already carries the matching Chrome UA string
 * (see {@link GfmHttpConfiguration}).
 */
public final class GfmFetchMetadataHeaders {

    private GfmFetchMetadataHeaders() {}

    public static RestClient.RequestHeadersSpec<?> forDocumentNavigation(RestClient.RequestHeadersSpec<?> spec) {
        return withCommonHeaders(spec)
                .header(
                        HttpHeaders.ACCEPT,
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,"
                                + "*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .header(HttpHeaders.CACHE_CONTROL, "max-age=0")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-User", "?1");
    }

    public static RestClient.RequestHeadersSpec<?> forXhr(RestClient.RequestHeadersSpec<?> spec) {
        return withCommonHeaders(spec)
                .header(HttpHeaders.ACCEPT, "application/json, text/plain, */*")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors");
    }

    private static RestClient.RequestHeadersSpec<?> withCommonHeaders(RestClient.RequestHeadersSpec<?> spec) {
        return spec.header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
                .header("DNT", "1")
                .header("Sec-Fetch-Site", "same-origin")
                .header("sec-ch-ua", "\"Not;A=Brand\";v=\"8\", \"Chromium\";v=\"150\", \"Google Chrome\";v=\"150\"")
                .header("sec-ch-ua-mobile", "?0")
                .header("sec-ch-ua-platform", "\"macOS\"");
    }
}
