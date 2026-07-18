package com.lava.swexpedited.service;

import com.lava.swexpedited.repository.SamsaraDriverDutyStatusRepository;
import com.lava.swexpedited.repository.SamsaraDriverRepository;
import com.lava.swexpedited.repository.VektorManifestRepository;
import com.lava.swexpedited.samsara.DriverTimelineRow;
import com.lava.swexpedited.samsara.SamsaraDriverDutyStatusRow;
import com.lava.swexpedited.samsara.SamsaraDriverRow;
import com.lava.swexpedited.vektor.VektorManifestRow;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Joins samsara_driver, samsara_driver_duty_status, and vektor_manifest in Java - same convention as
 * {@link SamsaraDriverServiceImpl}: three independently-synced tables, no cross-table transactional consistency to lean
 * on. A manifest is matched to a driver via {@code matchedSamsaraDriverId} (best-effort name match, see
 * {@code VektorDriverMatchStrategy}). If more than one currently-synced manifest matches the same driver (e.g. one load
 * just finished and another started between syncs), the one with the soonest {@code pickupAppointmentStart} wins - a
 * known MVP simplification, not a real dispatch rule; a manifest with no parseable pickup appointment loses that
 * tie-break to one that has it.
 */
@Service
@Transactional(readOnly = true)
public class DriverTimelineServiceImpl implements DriverTimelineService {

    private final SamsaraDriverRepository samsaraDriverRepository;
    private final SamsaraDriverDutyStatusRepository samsaraDriverDutyStatusRepository;
    private final VektorManifestRepository vektorManifestRepository;

    public DriverTimelineServiceImpl(
            SamsaraDriverRepository samsaraDriverRepository,
            SamsaraDriverDutyStatusRepository samsaraDriverDutyStatusRepository,
            VektorManifestRepository vektorManifestRepository) {
        this.samsaraDriverRepository = samsaraDriverRepository;
        this.samsaraDriverDutyStatusRepository = samsaraDriverDutyStatusRepository;
        this.vektorManifestRepository = vektorManifestRepository;
    }

    @Override
    public List<DriverTimelineRow> findAll() {
        Map<String, SamsaraDriverDutyStatusRow> dutyStatusesByDriverId =
                samsaraDriverDutyStatusRepository.findAll().stream()
                        .collect(Collectors.toMap(SamsaraDriverDutyStatusRow::driverId, Function.identity()));
        Map<String, VektorManifestRow> manifestsByDriverId = vektorManifestRepository.findAll().stream()
                .filter(manifest -> manifest.matchedSamsaraDriverId() != null)
                .collect(Collectors.toMap(
                        VektorManifestRow::matchedSamsaraDriverId,
                        Function.identity(),
                        DriverTimelineServiceImpl::soonestPickup));

        return samsaraDriverRepository.findAll().stream()
                .map(driver -> toRow(
                        driver,
                        Optional.ofNullable(dutyStatusesByDriverId.get(driver.id())),
                        Optional.ofNullable(manifestsByDriverId.get(driver.id()))))
                .toList();
    }

    private static VektorManifestRow soonestPickup(VektorManifestRow first, VektorManifestRow second) {
        return Comparator.comparing(
                                        VektorManifestRow::pickupAppointmentStart,
                                        Comparator.nullsLast(Comparator.naturalOrder()))
                                .compare(first, second)
                        <= 0
                ? first
                : second;
    }

    private DriverTimelineRow toRow(
            SamsaraDriverRow driver,
            Optional<SamsaraDriverDutyStatusRow> dutyStatus,
            Optional<VektorManifestRow> manifest) {
        return new DriverTimelineRow(
                driver.id(),
                driver.name(),
                driver.activationStatus(),
                dutyStatus.map(SamsaraDriverDutyStatusRow::dutyStatus).orElse(null),
                manifest.map(VektorManifestRow::status).orElse(null),
                manifest.map(VektorManifestRow::pickupAppointmentStart).orElse(null),
                manifest.map(VektorManifestRow::eta).orElse(null),
                manifest.map(VektorManifestRow::destination).orElse(null),
                manifest.map(VektorManifestRow::loadReference).orElse(null));
    }
}
