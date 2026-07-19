package com.lava.swexpedited.service;

import com.lava.swexpedited.batch.vektor.VektorEntityLocationClient;
import com.lava.swexpedited.batch.vektor.VektorSessionProvider;
import com.lava.swexpedited.boot.autoconfigure.app.VektorProperties;
import com.lava.swexpedited.manifest.ManifestDriverLocationResponse;
import com.lava.swexpedited.repository.VektorManifestRepository;
import com.lava.swexpedited.vektor.VektorDriverLocationRow;
import com.lava.swexpedited.vektor.VektorManifestRow;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Resolves a manifest's driver's live location via Vektor's own {@code EntityLocation/GetAll}, keyed directly by
 * Vektor's {@code driver_id} - every manifest already carries this with certainty (see {@code VektorManifestMapper}),
 * unlike Samsara's live location, which needs a best-effort name-matched {@code matched_samsara_driver_id} join that
 * can simply be null. Backs both the Schedule page's live driver marker (via the {@code /driver-location} endpoint) and
 * {@link ManifestRouteServiceImpl}'s route-splice, so the two always agree on where the driver is, sourced from the
 * same place. Live, on-demand, no persisted cache - same convention as {@link SamsaraDriverLiveLocationServiceImpl} and
 * {@link ManifestRouteServiceImpl}.
 *
 * <p>{@code EntityLocation/GetAll} takes no filter - it always returns every driver in the company - so this fetches
 * the full list on every call and picks whichever entry for the target driver has the most recent {@code asOf}.
 */
@Service
public class ManifestDriverLocationServiceImpl implements ManifestDriverLocationService {

    private final VektorManifestRepository vektorManifestRepository;
    private final VektorSessionProvider vektorSessionProvider;
    private final VektorEntityLocationClient vektorEntityLocationClient;
    private final VektorProperties vektorProperties;

    public ManifestDriverLocationServiceImpl(
            VektorManifestRepository vektorManifestRepository,
            VektorSessionProvider vektorSessionProvider,
            VektorEntityLocationClient vektorEntityLocationClient,
            VektorProperties vektorProperties) {
        this.vektorManifestRepository = vektorManifestRepository;
        this.vektorSessionProvider = vektorSessionProvider;
        this.vektorEntityLocationClient = vektorEntityLocationClient;
        this.vektorProperties = vektorProperties;
    }

    @Override
    public Optional<ManifestDriverLocationResponse> findLiveLocation(long manifestNumber) {
        return this.vektorManifestRepository
                .findByManifestNumber(manifestNumber)
                .map(VektorManifestRow::driverId)
                .filter(Objects::nonNull)
                .flatMap(this::findLiveLocationForDriver);
    }

    private Optional<ManifestDriverLocationResponse> findLiveLocationForDriver(String vektorDriverId) {
        List<VektorDriverLocationRow> locations = this.vektorSessionProvider.withSession(
                jwt -> this.vektorEntityLocationClient.fetchAll(jwt, this.vektorProperties.companyId()));

        return locations.stream()
                .filter(location -> vektorDriverId.equals(location.driverId()))
                .max(Comparator.comparing(
                        VektorDriverLocationRow::asOf, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(ManifestDriverLocationServiceImpl::toResponse);
    }

    private static ManifestDriverLocationResponse toResponse(VektorDriverLocationRow location) {
        return new ManifestDriverLocationResponse(
                location.latitude(),
                location.longitude(),
                location.headingDegrees(),
                location.asOf(),
                location.formattedLocation());
    }
}
