package com.lava.swexpedited.service;

import com.lava.swexpedited.repository.SamsaraDriverDutyStatusRepository;
import com.lava.swexpedited.repository.SamsaraDriverRepository;
import com.lava.swexpedited.repository.VektorManifestRepository;
import com.lava.swexpedited.samsara.DriverTimelineRow;
import com.lava.swexpedited.samsara.DriverTimelineRow.ManifestSegment;
import com.lava.swexpedited.samsara.SamsaraDriverDutyStatusRow;
import com.lava.swexpedited.samsara.SamsaraDriverRow;
import com.lava.swexpedited.vektor.VektorManifestRow;
import java.time.LocalDateTime;
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
 * on. Manifests are matched to a driver via {@code matchedSamsaraDriverId} (best-effort name match, see
 * {@code VektorDriverMatchStrategy}) and grouped, sorted by soonest {@code pickupAppointmentStart} first (nulls last)
 * so a driver's row lists their loads for the week in chronological order - a driver can have several manifests in one
 * week now that vektor_manifest retains history instead of only "what's active right now" (see
 * {@code VektorManifestRepository#upsertAll}'s javadoc).
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
    public List<DriverTimelineRow> findForWeek(LocalDateTime weekStart, LocalDateTime weekEnd) {
        Map<String, SamsaraDriverDutyStatusRow> dutyStatusesByDriverId =
                samsaraDriverDutyStatusRepository.findAll().stream()
                        .collect(Collectors.toMap(SamsaraDriverDutyStatusRow::driverId, Function.identity()));
        Map<String, List<VektorManifestRow>> manifestsByDriverId =
                vektorManifestRepository.findByAppointmentWindow(weekStart, weekEnd).stream()
                        .filter(manifest -> manifest.matchedSamsaraDriverId() != null)
                        .sorted(Comparator.comparing(
                                VektorManifestRow::pickupAppointmentStart,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .collect(Collectors.groupingBy(VektorManifestRow::matchedSamsaraDriverId));

        return samsaraDriverRepository.findAll().stream()
                .map(driver -> toRow(
                        driver,
                        Optional.ofNullable(dutyStatusesByDriverId.get(driver.id())),
                        manifestsByDriverId.getOrDefault(driver.id(), List.of())))
                .toList();
    }

    private DriverTimelineRow toRow(
            SamsaraDriverRow driver,
            Optional<SamsaraDriverDutyStatusRow> dutyStatus,
            List<VektorManifestRow> manifests) {
        return new DriverTimelineRow(
                driver.id(),
                driver.name(),
                driver.activationStatus(),
                dutyStatus.map(SamsaraDriverDutyStatusRow::dutyStatus).orElse(null),
                manifests.stream().map(DriverTimelineServiceImpl::toSegment).toList());
    }

    private static ManifestSegment toSegment(VektorManifestRow manifest) {
        return new ManifestSegment(
                manifest.manifestNumber(),
                manifest.status(),
                manifest.pickupAppointmentStart(),
                manifest.eta(),
                manifest.origin(),
                manifest.destination(),
                manifest.loadReference());
    }
}
