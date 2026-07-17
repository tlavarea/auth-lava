package com.lava.swexpedited.batch.samsara;

import com.lava.swexpedited.repository.SamsaraDriverRepository;
import com.lava.swexpedited.repository.SamsaraDriverVehicleAssignmentRepository;
import com.lava.swexpedited.samsara.SamsaraDriverRow;
import com.lava.swexpedited.samsara.SamsaraDriverVehicleAssignmentRow;
import com.lava.swexpedited.samsara.SamsaraDriverWithRaw;
import com.lava.swexpedited.samsara.model.Driver;
import com.lava.swexpedited.samsara.model.DriverStaticAssignedVehicle;
import com.lava.swexpedited.samsara.model.DriverVehicleAssignmentV2ObjectResponseBody;
import com.lava.swexpedited.samsara.model.TagTinyResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Fetches Samsara's active driver roster and each driver's current vehicle assignment, then replaces samsara_driver and
 * samsara_driver_vehicle_assignment in that order - the assignment table's driver_id FK cascades from a driver delete
 * (see 004-create-samsara-driver-vehicle-assignment.yaml), so the driver replace must commit first within this
 * tasklet's step transaction for the FK to resolve against the freshly-synced roster.
 */
@Component
@Slf4j
public class SamsaraDriverSyncTasklet extends SamsaraTasklet implements Tasklet {

    private final SamsaraFleetClient samsaraFleetClient;
    private final SamsaraDriverRepository samsaraDriverRepository;
    private final SamsaraDriverVehicleAssignmentRepository samsaraDriverVehicleAssignmentRepository;

    public SamsaraDriverSyncTasklet(
            SamsaraFleetClient samsaraFleetClient,
            SamsaraDriverRepository samsaraDriverRepository,
            SamsaraDriverVehicleAssignmentRepository samsaraDriverVehicleAssignmentRepository) {
        this.samsaraFleetClient = samsaraFleetClient;
        this.samsaraDriverRepository = samsaraDriverRepository;
        this.samsaraDriverVehicleAssignmentRepository = samsaraDriverVehicleAssignmentRepository;
    }

    @Override
    public @Nullable RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        List<SamsaraDriverWithRaw> driversWithRaw = this.samsaraFleetClient.fetchDrivers();
        List<SamsaraDriverRow> driverRows = driversWithRaw.stream()
                .map(SamsaraDriverSyncTasklet::toDriverRow)
                .toList();

        this.samsaraDriverRepository.replaceAll(driverRows);
        log.info("execute::stored {} samsara drivers", driverRows.size());

        List<String> driverIds = driversWithRaw.stream()
                .map(driverWithRaw -> driverWithRaw.payload().getId())
                .toList();
        List<SamsaraDriverVehicleAssignmentRow> assignmentRows =
                toAssignmentRows(driversWithRaw, this.samsaraFleetClient.fetchDriverVehicleAssignments(driverIds));

        this.samsaraDriverVehicleAssignmentRepository.replaceAll(assignmentRows);
        log.info("execute::stored {} samsara driver-vehicle assignments", assignmentRows.size());
        return RepeatStatus.FINISHED;
    }

    /**
     * Maps one {@code /fleet/drivers} entry, paired with its captured raw JSON, to a samsara_driver row.
     *
     * @param driverWithRaw - the Samsara driver payload and its captured raw JSON as a {@link SamsaraDriverWithRaw}
     *     object.
     * @return the driver data transformed to a {@link SamsaraDriverRow} object.
     */
    private static SamsaraDriverRow toDriverRow(SamsaraDriverWithRaw driverWithRaw) {
        Driver payload = driverWithRaw.payload();
        return new SamsaraDriverRow(
                payload.getId(),
                payload.getName(),
                payload.getUsername(),
                payload.getEmail(),
                payload.getPhone(),
                payload.getLicenseNumber(),
                payload.getLicenseState(),
                payload.getDriverActivationStatus() == null
                        ? null
                        : payload.getDriverActivationStatus().getValue(),
                joinTagNames(payload.getTags()),
                parseLocalDateTime(payload.getCreatedAtTime()),
                parseLocalDateTime(payload.getUpdatedAtTime()),
                driverWithRaw.rawJson(),
                null);
    }

    /**
     * Builds one row per driver with a known current vehicle - the driver's dynamic assignment from
     * {@code /fleet/driver-vehicle-assignments} when one was found, falling back to the driver's own
     * staticAssignedVehicle field (see {@link #toAssignmentRow}) for drivers the assignments endpoint didn't resolve at
     * all. In practice this fallback is defensive rather than load-bearing: {@link SamsaraFleetClient
     * #fetchDriverVehicleAssignments(List)}'s {@code driverIds}+{@code assignmentType=HOS} query has been observed to
     * resolve static-only assignments too, but that's undocumented Samsara behavior, not a guaranteed contract.
     *
     * @param driversWithRaw - every currently-active driver as a {@link List} of {@link SamsaraDriverWithRaw} objects.
     * @param dynamicAssignments - the raw, not-yet-deduplicated driver-vehicle-assignments API results as a
     *     {@link List} of {@link DriverVehicleAssignmentV2ObjectResponseBody} objects.
     * @return one {@link SamsaraDriverVehicleAssignmentRow} per driver with either a dynamic or a static assignment.
     */
    private static List<SamsaraDriverVehicleAssignmentRow> toAssignmentRows(
            List<SamsaraDriverWithRaw> driversWithRaw,
            List<DriverVehicleAssignmentV2ObjectResponseBody> dynamicAssignments) {
        Map<String, DriverVehicleAssignmentV2ObjectResponseBody> mostRecentDynamicByDriverId =
                dynamicAssignments.stream()
                        .filter(payload -> payload.getDriver() != null && payload.getVehicle() != null)
                        .collect(Collectors.toMap(
                                payload -> payload.getDriver().getId(),
                                Function.identity(),
                                SamsaraDriverSyncTasklet::mostRecent));

        return driversWithRaw.stream()
                .map(driverWithRaw -> toAssignmentRow(driverWithRaw.payload(), mostRecentDynamicByDriverId))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Builds one driver's current-vehicle row, preferring their dynamic assignment over their static one.
     *
     * @param driver - the driver as a {@link Driver} object.
     * @param mostRecentDynamicByDriverId - each driver's most recent dynamic assignment, keyed by driver id, as a
     *     {@link Map} of {@link String} to {@link DriverVehicleAssignmentV2ObjectResponseBody}.
     * @return the driver's current assignment as a {@link SamsaraDriverVehicleAssignmentRow} object, or null if the
     *     driver has neither a dynamic nor a static vehicle assignment.
     */
    private static @Nullable SamsaraDriverVehicleAssignmentRow toAssignmentRow(
            Driver driver, Map<String, DriverVehicleAssignmentV2ObjectResponseBody> mostRecentDynamicByDriverId) {
        DriverVehicleAssignmentV2ObjectResponseBody dynamic = mostRecentDynamicByDriverId.get(driver.getId());

        if (dynamic != null) {
            return new SamsaraDriverVehicleAssignmentRow(
                    driver.getId(),
                    dynamic.getVehicle().getId(),
                    dynamic.getVehicle().getName(),
                    parseLocalDateTime(dynamic.getStartTime()),
                    parseLocalDateTime(dynamic.getAssignedAtTime()),
                    null);
        }

        DriverStaticAssignedVehicle staticVehicle = driver.getStaticAssignedVehicle();

        if (staticVehicle != null && staticVehicle.getId() != null) {
            return new SamsaraDriverVehicleAssignmentRow(
                    driver.getId(), staticVehicle.getId(), staticVehicle.getName(), null, null, null);
        }

        return null;
    }

    /**
     * Picks whichever of two assignments for the same driver is more recent, so that if
     * {@link SamsaraFleetClient#fetchDriverVehicleAssignments(List)} ever returns more than one assignment for a driver
     * - it's not documented to, but its current resolution mode isn't a guaranteed contract either - this still yields
     * exactly one row per driver_id, as required by samsara_driver_vehicle_assignment's primary key.
     *
     * @param first - one candidate assignment as a {@link DriverVehicleAssignmentV2ObjectResponseBody} object.
     * @param second - the other candidate assignment as a {@link DriverVehicleAssignmentV2ObjectResponseBody} object.
     * @return whichever of the two has the later effective timestamp.
     */
    private static DriverVehicleAssignmentV2ObjectResponseBody mostRecent(
            DriverVehicleAssignmentV2ObjectResponseBody first, DriverVehicleAssignmentV2ObjectResponseBody second) {
        return effectiveTimestamp(first).compareTo(effectiveTimestamp(second)) >= 0 ? first : second;
    }

    /**
     * The timestamp {@link #mostRecent} orders assignments by - assignedAtTime when present, falling back to startTime,
     * since assignedAtTime is sometimes blank (see {@link SamsaraTasklet#parseLocalDateTime}).
     *
     * @param payload - the assignment as a {@link DriverVehicleAssignmentV2ObjectResponseBody} object.
     * @return the assignment's effective timestamp as an {@link OffsetDateTime} object, or {@link OffsetDateTime#MIN}
     *     if both assignedAtTime and startTime are blank.
     */
    private static OffsetDateTime effectiveTimestamp(DriverVehicleAssignmentV2ObjectResponseBody payload) {
        String timestamp = StringUtils.isNotBlank(payload.getAssignedAtTime())
                ? payload.getAssignedAtTime()
                : payload.getStartTime();
        return StringUtils.isNotBlank(timestamp) ? OffsetDateTime.parse(timestamp) : OffsetDateTime.MIN;
    }

    /**
     * Joins a driver's tag names into a single comma-separated string, or null if the driver has no tags.
     *
     * @param tags - the driver's tags as a {@link List} of {@link TagTinyResponse} objects.
     * @return the tag names joined into a comma-separated {@link String}, or null if there are no tags.
     */
    private static @Nullable String joinTagNames(@Nullable List<TagTinyResponse> tags) {
        if (CollectionUtils.isNotEmpty(tags)) {
            return tags.stream().map(TagTinyResponse::getName).collect(Collectors.joining(","));
        }

        return null;
    }
}
