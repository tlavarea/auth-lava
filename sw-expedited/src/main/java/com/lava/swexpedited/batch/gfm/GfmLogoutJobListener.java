package com.lava.swexpedited.batch.gfm;

import com.lava.swexpedited.boot.autoconfigure.app.GfmProperties;
import com.lava.swexpedited.logging.LogSanitizer;
import java.net.URI;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Best-effort session cleanup after each sync run - GFM/TEAMS caps the number of concurrent sessions allowed per
 * credential, so without an explicit logout, sessions accumulate server-side across repeated sync cycles until the
 * credential gets locked out of logging in at all. Ported from scjj-gfm-app's CurrentShipmentsJobListener: same two
 * logout calls, same unconditional/independent try-catch-and-continue shape (no retry - if a logout fails, the session
 * just degrades until it expires naturally, which is an acceptable outcome here).
 *
 * <p>{@code afterJob} runs whether the job COMPLETED or FAILED, as long as it actually started - exactly when a
 * partially-established session still needs releasing.
 *
 * <p>GFM's logout endpoint expects the value of the XSRF-TOKEN cookie the login chain sets, played back as the
 * CSRFToken query param - not a separately-configured value - so this reads it from the same cookie store the tasklet's
 * RestClient uses, once the login has had a chance to populate it.
 *
 * <p>{@code gfmCookieStore} is a singleton bean shared by every job run for the lifetime of the process, not recreated
 * per run - so once the logout calls above have had their (best-effort, possibly incomplete) say, this unconditionally
 * clears the local jar rather than trusting the server's Set-Cookie responses to expire everything. Confirmed against
 * real browser behavior: logging out of GFM and clicking back in forces the full ECA-PKI login page again, while
 * skipping logout lets a later click go straight back into GFM on the old session - i.e. stale client-side cookies
 * surviving a logout is exactly the kind of state that makes the next run's fresh login collide with a leftover
 * gfm.transport.mil session (JSESSIONID/gfm_session/loginScac/etaUserId) instead of starting clean.
 */
@Component
@Slf4j
public class GfmLogoutJobListener implements JobExecutionListener {

    private static final String XSRF_TOKEN_COOKIE = "XSRF-TOKEN";

    private final RestClient gfmRestClient;
    private final CookieStore gfmCookieStore;
    private final GfmProperties gfmProperties;

    public GfmLogoutJobListener(
            @Qualifier("gfmRestClient") RestClient gfmRestClient,
            @Qualifier("gfmCookieStore") CookieStore gfmCookieStore,
            GfmProperties gfmProperties) {
        this.gfmRestClient = gfmRestClient;
        this.gfmCookieStore = gfmCookieStore;
        this.gfmProperties = gfmProperties;
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info(
                "afterJob::id: {} | exitStatus: {}",
                jobExecution.getJobInstanceId(),
                LogSanitizer.sanitize(jobExecution.getExitStatus()));

        Optional<String> csrfToken = this.gfmCookieStore.getCookies().stream()
                .filter(cookie -> XSRF_TOKEN_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();

        if (csrfToken.isPresent()) {
            try {
                URI gfmLogoutUri = UriComponentsBuilder.fromUriString(this.gfmProperties.gfmBaseUrl())
                        .path("/gfmgateway/mod/logout")
                        .queryParam("CSRFToken", csrfToken.get())
                        .build()
                        .toUri();
                this.gfmRestClient
                        .post()
                        .uri(gfmLogoutUri)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                        .retrieve()
                        .toBodilessEntity();
                log.info("afterJob::GFM logout successful");
            } catch (Exception e) {
                log.error("afterJob::GFM logout failed", e);
            }
        } else {
            log.debug("afterJob::no XSRF-TOKEN cookie present, skipping GFM logout");
        }

        try {
            URI mpsLogoutUri = UriComponentsBuilder.fromUriString(this.gfmProperties.mpsKmisBaseUrl())
                    .path("/oidc/logout")
                    .build()
                    .toUri();
            this.gfmRestClient.post().uri(mpsLogoutUri).retrieve().toBodilessEntity();
            log.info("afterJob::MPS logout successful");
        } catch (Exception e) {
            log.error("afterJob::MPS logout failed", e);
        }

        this.gfmCookieStore.clear();
        log.debug("afterJob::cleared local cookie jar");
    }
}
