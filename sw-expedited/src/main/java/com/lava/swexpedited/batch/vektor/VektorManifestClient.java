package com.lava.swexpedited.batch.vektor;

import com.lava.swexpedited.vektor.VektorGrpcWeb;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Fetches Vektor manifests via {@code Manifests/Get}, filtered to the configured {@code effective_status} values and a
 * bounded {@code first_last_stop_appointment_start_datetime_range}, sorted by first-stop appointment start (most recent
 * first) - the exact request shape Vektor's own UI sends when a dispatcher filters the manifest list, reverse-
 * engineered from real captured requests (see the Vektor manifest sync plan). The date-range bound matters even though
 * every status is synced as history via upsert: without it, a wide status list (e.g. including
 * {@code manifest_delivered}) would re-fetch the account's entire manifest history every sync cycle, forever - Vektor's
 * own UI never queries without one either. Returns raw {@link VektorGrpcWeb.Message}s rather than a typed model, since
 * there's no schema to generate one from - {@code VektorManifestMapper} is what knows which field numbers mean what.
 *
 * <p>Pagination is real (confirmed via a captured request/response pair returning a 232-of-300 total with a 50-row
 * page): request field {@code 1} is a 0-based offset (omitted, per protobuf convention, when {@code 0}), field
 * {@code 2} is the page size, and the response's field {@code 1} is the total row count matching the filter.
 */
@Component
public class VektorManifestClient extends VektorClient {

    private static final int PAGE_SIZE = 200;

    private final RestClient vektorRestClient;
    private final Duration retryBackoff;

    public VektorManifestClient(
            @Qualifier("vektorRestClient") RestClient vektorRestClient,
            @Value("${vektor.retry-backoff:5s}") Duration retryBackoff) {
        this.vektorRestClient = vektorRestClient;
        this.retryBackoff = retryBackoff;
    }

    public List<VektorGrpcWeb.Message> fetchManifests(
            String jwt, String companyId, List<String> effectiveStatuses, LocalDate windowStart, LocalDate windowEnd) {
        List<VektorGrpcWeb.Message> allManifests = new ArrayList<>();
        long offset = 0;

        while (true) {
            VektorGrpcWeb.Message page = fetchPage(jwt, companyId, effectiveStatuses, windowStart, windowEnd, offset);
            List<VektorGrpcWeb.Message> pageManifests = page.getMessages(3);
            allManifests.addAll(pageManifests);
            long total = page.getVarint(1).orElse((long) allManifests.size());

            if (pageManifests.isEmpty() || allManifests.size() >= total) {
                break;
            }
            offset += pageManifests.size();
        }

        return allManifests;
    }

    private VektorGrpcWeb.Message fetchPage(
            String jwt,
            String companyId,
            List<String> effectiveStatuses,
            LocalDate windowStart,
            LocalDate windowEnd,
            long offset) {
        VektorGrpcWeb.Writer request = new VektorGrpcWeb.Writer();
        if (offset > 0) {
            request.writeVarint(1, offset);
        }
        request.writeVarint(2, PAGE_SIZE)
                .writeString(3, "first_stop_appointment_start_datetime")
                .writeString(4, "desc")
                .writeMessage(5, statusFilter(effectiveStatuses))
                .writeMessage(5, dateRangeFilter(windowStart, windowEnd));

        ResponseEntity<byte[]> response = retrying(
                () -> this.vektorRestClient
                        .post()
                        .uri("/carrier/dashboard/core/envoy/Manifests/Get")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                        .header("company_id", companyId)
                        .body(VektorGrpcWeb.encodeUnaryRequest(request))
                        .retrieve()
                        .toEntity(byte[].class),
                this.retryBackoff);

        return VektorGrpcWeb.decodeUnaryResponse(requireBody(response, "Manifests/Get"));
    }

    private VektorGrpcWeb.Writer statusFilter(List<String> effectiveStatuses) {
        VektorGrpcWeb.Writer statusValues = new VektorGrpcWeb.Writer();
        for (String status : effectiveStatuses) {
            statusValues.writeString(1, status);
        }
        VektorGrpcWeb.Writer statusValuesWrapper = new VektorGrpcWeb.Writer().writeMessage(1, statusValues);
        return new VektorGrpcWeb.Writer()
                .writeString(1, "effective_status")
                .writeString(2, "is")
                .writeMessage(3, statusValuesWrapper);
    }

    private VektorGrpcWeb.Writer dateRangeFilter(LocalDate windowStart, LocalDate windowEnd) {
        VektorGrpcWeb.Writer range = new VektorGrpcWeb.Writer()
                .writeString(1, windowStart.toString())
                .writeString(2, windowEnd.toString());
        VektorGrpcWeb.Writer rangeWrapper = new VektorGrpcWeb.Writer().writeMessage(4, range);
        return new VektorGrpcWeb.Writer()
                .writeString(1, "first_last_stop_appointment_start_datetime_range")
                .writeString(2, "intersects")
                .writeMessage(3, rangeWrapper);
    }
}
