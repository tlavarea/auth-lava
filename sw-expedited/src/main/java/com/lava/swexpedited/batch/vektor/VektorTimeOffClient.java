package com.lava.swexpedited.batch.vektor;

import com.lava.swexpedited.vektor.VektorGrpcWeb;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Fetches driver time-off blocks via {@code TruckTimeOff/Get} - reverse-engineered from a real captured request/
 * response pair (see the Vektor manifest sync plan). Despite the name, results are grouped by <em>truck</em>, not
 * driver - a UUID space entirely distinct from Vektor's driver_id (see {@code VektorManifestMapper}'s javadoc) - so
 * this returns the flattened list of individual time-off entries (each still carrying its own truck_id), leaving
 * truck-&gt;driver attribution to {@code VektorSyncTasklet} (via {@code VektorManifestRepository
 * #findLatestDriverIdByTruckId}).
 *
 * <p>The request carries a single lower-bound date; the one captured response returned entries for months beyond that
 * date, suggesting the endpoint has no upper bound and simply returns everything from that date forward - but whether
 * entries starting <em>before</em> the given date are excluded is unconfirmed (only one capture exists).
 * {@code fromDate} is deliberately conservative (see {@code VektorSyncTasklet}) to avoid missing an in-progress
 * time-off block that started before "today" if that suspicion turns out to be true.
 */
@Component
public class VektorTimeOffClient extends VektorClient {

    private static final DateTimeFormatter FROM_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestClient vektorRestClient;
    private final Duration retryBackoff;

    public VektorTimeOffClient(
            @Qualifier("vektorRestClient") RestClient vektorRestClient,
            @Value("${vektor.retry-backoff:5s}") Duration retryBackoff) {
        this.vektorRestClient = vektorRestClient;
        this.retryBackoff = retryBackoff;
    }

    public List<VektorGrpcWeb.Message> fetchTimeOff(String jwt, String companyId, LocalDate fromDate) {
        VektorGrpcWeb.Writer request = new VektorGrpcWeb.Writer()
                .writeString(1, fromDate.atStartOfDay().format(FROM_DATE_FORMAT));

        ResponseEntity<byte[]> response = retrying(
                () -> this.vektorRestClient
                        .post()
                        .uri("/carrier/dashboard/fleet/envoy/TruckTimeOff/Get")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                        .header("company_id", companyId)
                        .body(VektorGrpcWeb.encodeUnaryRequest(request))
                        .retrieve()
                        .toEntity(byte[].class),
                this.retryBackoff);

        VektorGrpcWeb.Message decoded = VektorGrpcWeb.decodeUnaryResponse(requireBody(response, "TruckTimeOff/Get"));

        return decoded.getMessages(1).stream()
                .flatMap(truckGroup -> truckGroup.getMessage(2).stream())
                .flatMap(entriesWrapper -> entriesWrapper.getMessages(1).stream())
                .toList();
    }
}
