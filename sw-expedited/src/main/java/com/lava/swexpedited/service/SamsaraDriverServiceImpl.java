package com.lava.swexpedited.service;

import com.lava.swexpedited.repository.SamsaraDriverDutyStatusRepository;
import com.lava.swexpedited.repository.SamsaraDriverRepository;
import com.lava.swexpedited.repository.SamsaraDriverVehicleAssignmentRepository;
import com.lava.swexpedited.repository.SamsaraVehicleLocationRepository;
import com.lava.swexpedited.samsara.DriverDetailResponse;
import com.lava.swexpedited.samsara.DriverListingRow;
import com.lava.swexpedited.samsara.SamsaraDriverDutyStatusRow;
import com.lava.swexpedited.samsara.SamsaraDriverRow;
import com.lava.swexpedited.samsara.SamsaraDriverVehicleAssignmentRow;
import com.lava.swexpedited.samsara.SamsaraVehicleLocationRow;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Joins samsara_driver, samsara_driver_vehicle_assignment, samsara_vehicle_location, and samsara_driver_duty_status in
 * Java rather than SQL - the four tables are replaced on independent cadences by independent sync jobs (see
 * {@code SamsaraDriverSyncTasklet}, {@code SamsaraLocationSyncTasklet}, and
 * {@code SamsaraDriverDutyStatusSyncTasklet}), so there's no cross-table transactional consistency to lean on, and a
 * join query would just be reimplementing this same left-join-with-nulls logic in SQL for four small in-memory tables.
 * Driver is always the required/outer side; assignment, location, and duty status are all optional.
 */
@Service
@Transactional(readOnly = true)
public class SamsaraDriverServiceImpl implements SamsaraDriverService {

    private final SamsaraDriverRepository samsaraDriverRepository;
    private final SamsaraDriverVehicleAssignmentRepository samsaraDriverVehicleAssignmentRepository;
    private final SamsaraVehicleLocationRepository samsaraVehicleLocationRepository;
    private final SamsaraDriverDutyStatusRepository samsaraDriverDutyStatusRepository;

    public SamsaraDriverServiceImpl(
            SamsaraDriverRepository samsaraDriverRepository,
            SamsaraDriverVehicleAssignmentRepository samsaraDriverVehicleAssignmentRepository,
            SamsaraVehicleLocationRepository samsaraVehicleLocationRepository,
            SamsaraDriverDutyStatusRepository samsaraDriverDutyStatusRepository) {
        this.samsaraDriverRepository = samsaraDriverRepository;
        this.samsaraDriverVehicleAssignmentRepository = samsaraDriverVehicleAssignmentRepository;
        this.samsaraVehicleLocationRepository = samsaraVehicleLocationRepository;
        this.samsaraDriverDutyStatusRepository = samsaraDriverDutyStatusRepository;
    }

    @Override
    public List<DriverListingRow> findAll() {
        Map<String, SamsaraDriverVehicleAssignmentRow> assignmentsByDriverId =
                samsaraDriverVehicleAssignmentRepository.findAll().stream()
                        .collect(Collectors.toMap(SamsaraDriverVehicleAssignmentRow::driverId, Function.identity()));
        Map<String, SamsaraVehicleLocationRow> locationsByVehicleId =
                samsaraVehicleLocationRepository.findAll().stream()
                        .collect(Collectors.toMap(SamsaraVehicleLocationRow::vehicleId, Function.identity()));
        Map<String, SamsaraDriverDutyStatusRow> dutyStatusesByDriverId =
                samsaraDriverDutyStatusRepository.findAll().stream()
                        .collect(Collectors.toMap(SamsaraDriverDutyStatusRow::driverId, Function.identity()));

        return samsaraDriverRepository.findAll().stream()
                .map(driver -> {
                    Optional<SamsaraDriverVehicleAssignmentRow> assignment =
                            Optional.ofNullable(assignmentsByDriverId.get(driver.id()));
                    Optional<SamsaraVehicleLocationRow> location = assignment
                            .map(SamsaraDriverVehicleAssignmentRow::vehicleId)
                            .map(locationsByVehicleId::get);

                    return new DriverListingRow(
                            driver.id(),
                            driver.name(),
                            driver.activationStatus(),
                            assignment
                                    .map(SamsaraDriverVehicleAssignmentRow::vehicleName)
                                    .orElse(null),
                            Optional.ofNullable(dutyStatusesByDriverId.get(driver.id()))
                                    .map(SamsaraDriverDutyStatusRow::dutyStatus)
                                    .orElse(null),
                            location.map(SamsaraVehicleLocationRow::formattedLocation)
                                    .orElse(null));
                })
                .toList();
    }

    @Override
    public Optional<DriverDetailResponse> findDetail(String driverId) {
        return samsaraDriverRepository.findById(driverId).map(driver -> {
            Optional<SamsaraDriverVehicleAssignmentRow> assignment =
                    samsaraDriverVehicleAssignmentRepository.findByDriverId(driverId);
            Optional<SamsaraVehicleLocationRow> location =
                    assignment.flatMap(a -> samsaraVehicleLocationRepository.findByVehicleId(a.vehicleId()));
            Optional<SamsaraDriverDutyStatusRow> dutyStatus =
                    samsaraDriverDutyStatusRepository.findByDriverId(driverId);

            return toDetailResponse(driver, assignment, location, dutyStatus);
        });
    }

    private DriverDetailResponse toDetailResponse(
            SamsaraDriverRow driver,
            Optional<SamsaraDriverVehicleAssignmentRow> assignment,
            Optional<SamsaraVehicleLocationRow> location,
            Optional<SamsaraDriverDutyStatusRow> dutyStatus) {
        return new DriverDetailResponse(
                driver.id(),
                driver.name(),
                driver.username(),
                driver.email(),
                driver.phone(),
                driver.licenseNumber(),
                driver.licenseState(),
                driver.activationStatus(),
                dutyStatus.map(SamsaraDriverDutyStatusRow::dutyStatus).orElse(null),
                dutyStatus
                        .map(SamsaraDriverDutyStatusRow::driveRemainingDurationMs)
                        .orElse(null),
                dutyStatus
                        .map(SamsaraDriverDutyStatusRow::shiftRemainingDurationMs)
                        .orElse(null),
                dutyStatus
                        .map(SamsaraDriverDutyStatusRow::cycleRemainingDurationMs)
                        .orElse(null),
                dutyStatus
                        .map(SamsaraDriverDutyStatusRow::timeUntilBreakDurationMs)
                        .orElse(null),
                dutyStatus.map(SamsaraDriverDutyStatusRow::dutyStatusSince).orElse(null),
                driver.tags(),
                assignment.map(SamsaraDriverVehicleAssignmentRow::vehicleId).orElse(null),
                assignment.map(SamsaraDriverVehicleAssignmentRow::vehicleName).orElse(null),
                location.map(SamsaraVehicleLocationRow::latitude).orElse(null),
                location.map(SamsaraVehicleLocationRow::longitude).orElse(null),
                location.map(SamsaraVehicleLocationRow::heading).orElse(null),
                location.map(SamsaraVehicleLocationRow::speed).orElse(null),
                location.map(SamsaraVehicleLocationRow::locationTime).orElse(null),
                location.map(SamsaraVehicleLocationRow::formattedLocation).orElse(null),
                driver.rawResponse(),
                driver.syncedAt());
    }
}
