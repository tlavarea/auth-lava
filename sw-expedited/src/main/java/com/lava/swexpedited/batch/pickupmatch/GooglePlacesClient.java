package com.lava.swexpedited.batch.pickupmatch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lava.swexpedited.batch.RetryingHttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Resolves a free-text address to its place's canonical display name via Google's Places API (New) Text Search - backs
 * {@code ManifestRouteServiceImpl}'s retry for waypoints Google's Routes API won't compute a drivable route to/from at
 * their exact coordinates (see that class's javadoc). Deliberately returns only the resolved place's display name, not
 * its own location/place ID: both of those resolve back to the same precise coordinate the original request already
 * failed on (confirmed against Fort Hunter Liggett - Places API's own {@code location} for that address is identical to
 * Vektor's stored coordinates), whereas the bare display name text, re-geocoded by Routes API's own coarser address
 * resolution, reliably lands on a routable point instead.
 */
@Component
public class GooglePlacesClient extends RetryingHttpClient {

    private final RestClient googlePlacesRestClient;
    private final Duration retryBackoff;

    public GooglePlacesClient(
            @Qualifier("googlePlacesRestClient") RestClient googlePlacesRestClient,
            @Value("${google-places.retry-backoff:5s}") Duration retryBackoff) {
        this.googlePlacesRestClient = googlePlacesRestClient;
        this.retryBackoff = retryBackoff;
    }

    /**
     * Empty when {@code address} doesn't resolve to any place, or its resolved place has no display name - both
     * expected states (the caller falls back to the original coordinate; see {@code ManifestRouteServiceImpl}), not
     * errors.
     */
    public Optional<String> resolveDisplayName(String address) {
        SearchTextRequest request = new SearchTextRequest(address);

        SearchTextResponse response = retrying(
                () -> this.googlePlacesRestClient
                        .post()
                        .uri("/v1/places:searchText")
                        .body(request)
                        .retrieve()
                        .body(SearchTextResponse.class),
                this.retryBackoff);

        if (response == null || response.places() == null || response.places().isEmpty()) {
            return Optional.empty();
        }

        DisplayName displayName = response.places().getFirst().displayName();
        return displayName == null ? Optional.empty() : Optional.ofNullable(displayName.text());
    }

    record SearchTextRequest(String textQuery) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchTextResponse(List<Place> places) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Place(DisplayName displayName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DisplayName(String text) {}
}
