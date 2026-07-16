package com.lava.swexpedited.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.repository.SamsaraDriverRepository;
import com.lava.swexpedited.repository.SamsaraDriverVehicleAssignmentRepository;
import com.lava.swexpedited.samsara.SamsaraDriverRow;
import com.lava.swexpedited.samsara.SamsaraDriverVehicleAssignmentRow;
import com.lava.swexpedited.samsara.SamsaraDriverWithRaw;
import com.lava.swexpedited.samsara.model.Driver;
import com.lava.swexpedited.samsara.model.DriverActivationStatus;
import com.lava.swexpedited.samsara.model.DriverStaticAssignedVehicle;
import com.lava.swexpedited.samsara.model.DriverVehicleAssignmentV2ObjectResponseBody;
import com.lava.swexpedited.samsara.model.GoaDriverTinyResponseResponseBody;
import com.lava.swexpedited.samsara.model.GoaVehicleTinyResponseResponseBody;
import com.lava.swexpedited.samsara.model.TagTinyResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

@ExtendWith(MockitoExtension.class)
class SamsaraDriverSyncTaskletTest {

    @Mock
    private SamsaraFleetClient samsaraFleetClient;

    @Mock
    private SamsaraDriverRepository samsaraDriverRepository;

    @Mock
    private SamsaraDriverVehicleAssignmentRepository samsaraDriverVehicleAssignmentRepository;

    @Test
    void execute_mapsPayloadsAndReplacesDriverTableBeforeAssignmentTable() {
        Driver payload = new Driver()
                .id("41000123")
                .name("Jane Trucker")
                .username("jtrucker")
                .email("jane.trucker@example.com")
                .phone("555-0100")
                .licenseNumber("D1234567")
                .licenseState("TX")
                .driverActivationStatus(DriverActivationStatus.ACTIVE)
                .tags(List.of(
                        new TagTinyResponse().id("1").name("expedited"),
                        new TagTinyResponse().id("2").name("reefer")))
                .createdAtTime("2025-01-01T00:00:00Z")
                .updatedAtTime("2025-06-01T00:00:00-05:00");
        when(this.samsaraFleetClient.fetchDrivers())
                .thenReturn(List.of(new SamsaraDriverWithRaw(payload, "{\"id\":\"41000123\"}")));

        DriverVehicleAssignmentV2ObjectResponseBody assignmentPayload =
                new DriverVehicleAssignmentV2ObjectResponseBody()
                        .driver(new GoaDriverTinyResponseResponseBody()
                                .id("41000123")
                                .name("Jane Trucker"))
                        .vehicle(new GoaVehicleTinyResponseResponseBody()
                                .id("281474")
                                .name("Truck 12"))
                        .startTime("2026-07-01T00:00:00Z")
                        .endTime(null)
                        .assignedAtTime("2026-07-01T00:05:00-05:00");
        when(this.samsaraFleetClient.fetchDriverVehicleAssignments(Mockito.anyList()))
                .thenReturn(List.of(assignmentPayload));

        SamsaraDriverSyncTasklet tasklet = new SamsaraDriverSyncTasklet(
                this.samsaraFleetClient, this.samsaraDriverRepository, this.samsaraDriverVehicleAssignmentRepository);

        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);

        ArgumentCaptor<List<SamsaraDriverRow>> driverRowsCaptor = ArgumentCaptor.captor();
        Mockito.verify(this.samsaraDriverRepository).replaceAll(driverRowsCaptor.capture());
        assertThat(driverRowsCaptor.getValue()).hasSize(1);
        SamsaraDriverRow driverRow = driverRowsCaptor.getValue().getFirst();
        assertThat(driverRow.id()).isEqualTo("41000123");
        assertThat(driverRow.name()).isEqualTo("Jane Trucker");
        assertThat(driverRow.username()).isEqualTo("jtrucker");
        assertThat(driverRow.email()).isEqualTo("jane.trucker@example.com");
        assertThat(driverRow.phone()).isEqualTo("555-0100");
        assertThat(driverRow.licenseNumber()).isEqualTo("D1234567");
        assertThat(driverRow.licenseState()).isEqualTo("TX");
        assertThat(driverRow.activationStatus()).isEqualTo("active");
        assertThat(driverRow.tags()).isEqualTo("expedited,reefer");
        assertThat(driverRow.createdAtTime()).isEqualTo(LocalDateTime.of(2025, 1, 1, 0, 0, 0));
        assertThat(driverRow.updatedAtTime()).isEqualTo(LocalDateTime.of(2025, 6, 1, 0, 0, 0));
        assertThat(driverRow.rawResponse()).isEqualTo("{\"id\":\"41000123\"}");
        assertThat(driverRow.syncedAt()).isNull();

        ArgumentCaptor<List<SamsaraDriverVehicleAssignmentRow>> assignmentRowsCaptor = ArgumentCaptor.captor();
        Mockito.verify(this.samsaraDriverVehicleAssignmentRepository).replaceAll(assignmentRowsCaptor.capture());
        assertThat(assignmentRowsCaptor.getValue()).hasSize(1);
        SamsaraDriverVehicleAssignmentRow assignmentRow =
                assignmentRowsCaptor.getValue().getFirst();
        assertThat(assignmentRow.driverId()).isEqualTo("41000123");
        assertThat(assignmentRow.vehicleId()).isEqualTo("281474");
        assertThat(assignmentRow.vehicleName()).isEqualTo("Truck 12");
        assertThat(assignmentRow.startTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0, 0));
        assertThat(assignmentRow.assignedAtTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 5, 0));
        assertThat(assignmentRow.syncedAt()).isNull();

        InOrder inOrder = Mockito.inOrder(this.samsaraDriverRepository, this.samsaraDriverVehicleAssignmentRepository);
        inOrder.verify(this.samsaraDriverRepository).replaceAll(Mockito.anyList());
        inOrder.verify(this.samsaraDriverVehicleAssignmentRepository).replaceAll(Mockito.anyList());
    }

    @Test
    void execute_driverWithNoTags_tagsIsNull() {
        Driver payload = new Driver()
                .id("41000123")
                .name("Jane Trucker")
                .driverActivationStatus(DriverActivationStatus.ACTIVE)
                .tags(List.of());
        when(this.samsaraFleetClient.fetchDrivers())
                .thenReturn(List.of(new SamsaraDriverWithRaw(payload, "{\"id\":\"41000123\"}")));
        when(this.samsaraFleetClient.fetchDriverVehicleAssignments(Mockito.anyList()))
                .thenReturn(List.of());

        SamsaraDriverSyncTasklet tasklet = new SamsaraDriverSyncTasklet(
                this.samsaraFleetClient, this.samsaraDriverRepository, this.samsaraDriverVehicleAssignmentRepository);

        tasklet.execute(null, null);

        ArgumentCaptor<List<SamsaraDriverRow>> driverRowsCaptor = ArgumentCaptor.captor();
        Mockito.verify(this.samsaraDriverRepository).replaceAll(driverRowsCaptor.capture());
        assertThat(driverRowsCaptor.getValue().getFirst().tags()).isNull();
        assertThat(driverRowsCaptor.getValue().getFirst().createdAtTime()).isNull();
        assertThat(driverRowsCaptor.getValue().getFirst().updatedAtTime()).isNull();
    }

    @Test
    void execute_driverWithNeitherDynamicNorStaticAssignment_noRowCreated() {
        when(this.samsaraFleetClient.fetchDrivers())
                .thenReturn(List.of(new SamsaraDriverWithRaw(activeDriver("41000123"), "{}")));
        when(this.samsaraFleetClient.fetchDriverVehicleAssignments(Mockito.anyList()))
                .thenReturn(List.of());

        SamsaraDriverSyncTasklet tasklet = new SamsaraDriverSyncTasklet(
                this.samsaraFleetClient, this.samsaraDriverRepository, this.samsaraDriverVehicleAssignmentRepository);

        tasklet.execute(null, null);

        Mockito.verify(this.samsaraDriverVehicleAssignmentRepository).replaceAll(List.of());
    }

    @Test
    void execute_assignmentMissingDriverOrVehicleRef_neitherContributesARow() {
        when(this.samsaraFleetClient.fetchDrivers())
                .thenReturn(List.of(new SamsaraDriverWithRaw(activeDriver("41000123"), "{}")));
        // No driver ref at all - can never match any synced driver.
        DriverVehicleAssignmentV2ObjectResponseBody missingDriver = new DriverVehicleAssignmentV2ObjectResponseBody()
                .vehicle(new GoaVehicleTinyResponseResponseBody().id("281474").name("Truck 12"));
        // Driver ref matches our one synced driver, but no vehicle - must not produce a row for that driver either.
        DriverVehicleAssignmentV2ObjectResponseBody missingVehicle = new DriverVehicleAssignmentV2ObjectResponseBody()
                .driver(new GoaDriverTinyResponseResponseBody().id("41000123").name("Jane Trucker"));
        when(this.samsaraFleetClient.fetchDriverVehicleAssignments(Mockito.anyList()))
                .thenReturn(List.of(missingDriver, missingVehicle));

        SamsaraDriverSyncTasklet tasklet = new SamsaraDriverSyncTasklet(
                this.samsaraFleetClient, this.samsaraDriverRepository, this.samsaraDriverVehicleAssignmentRepository);

        tasklet.execute(null, null);

        Mockito.verify(this.samsaraDriverVehicleAssignmentRepository).replaceAll(List.of());
    }

    @Test
    void execute_assignmentWithBlankTimestamps_treatedAsAbsentNotParseFailure() {
        when(this.samsaraFleetClient.fetchDrivers())
                .thenReturn(List.of(new SamsaraDriverWithRaw(activeDriver("41000123"), "{}")));
        // Samsara sends "" rather than omitting the field for some assignment timestamps in practice (e.g. a
        // static assignment with no assignedAtTime event) - this must not throw DateTimeParseException.
        DriverVehicleAssignmentV2ObjectResponseBody blankTimestamps = new DriverVehicleAssignmentV2ObjectResponseBody()
                .driver(new GoaDriverTinyResponseResponseBody().id("41000123").name("Jane Trucker"))
                .vehicle(new GoaVehicleTinyResponseResponseBody().id("281474").name("Truck 12"))
                .startTime("")
                .assignedAtTime("");
        when(this.samsaraFleetClient.fetchDriverVehicleAssignments(Mockito.anyList()))
                .thenReturn(List.of(blankTimestamps));

        SamsaraDriverSyncTasklet tasklet = new SamsaraDriverSyncTasklet(
                this.samsaraFleetClient, this.samsaraDriverRepository, this.samsaraDriverVehicleAssignmentRepository);

        tasklet.execute(null, null);

        ArgumentCaptor<List<SamsaraDriverVehicleAssignmentRow>> captor = ArgumentCaptor.captor();
        Mockito.verify(this.samsaraDriverVehicleAssignmentRepository).replaceAll(captor.capture());
        SamsaraDriverVehicleAssignmentRow row = captor.getValue().getFirst();
        assertThat(row.startTime()).isNull();
        assertThat(row.assignedAtTime()).isNull();
    }

    @Test
    void execute_multipleAssignmentsForSameDriver_keepsOnlyTheMostRecent() {
        // fetchDriverVehicleAssignments(List) isn't documented to return more than one assignment per driver, but
        // its current resolution mode is undocumented Samsara behavior, not a guaranteed contract - if it ever does,
        // only the most recent one may survive, since driver_id is the table's primary key.
        when(this.samsaraFleetClient.fetchDrivers())
                .thenReturn(List.of(new SamsaraDriverWithRaw(activeDriver("41000123"), "{}")));
        DriverVehicleAssignmentV2ObjectResponseBody older = new DriverVehicleAssignmentV2ObjectResponseBody()
                .driver(new GoaDriverTinyResponseResponseBody().id("41000123").name("Jane Trucker"))
                .vehicle(new GoaVehicleTinyResponseResponseBody().id("281474").name("Truck 12"))
                .assignedAtTime("2026-07-15T00:00:00Z");
        DriverVehicleAssignmentV2ObjectResponseBody newer = new DriverVehicleAssignmentV2ObjectResponseBody()
                .driver(new GoaDriverTinyResponseResponseBody().id("41000123").name("Jane Trucker"))
                .vehicle(new GoaVehicleTinyResponseResponseBody().id("281475").name("Truck 13"))
                .assignedAtTime("2026-07-16T00:00:00Z");
        when(this.samsaraFleetClient.fetchDriverVehicleAssignments(Mockito.anyList()))
                .thenReturn(List.of(older, newer));

        SamsaraDriverSyncTasklet tasklet = new SamsaraDriverSyncTasklet(
                this.samsaraFleetClient, this.samsaraDriverRepository, this.samsaraDriverVehicleAssignmentRepository);

        tasklet.execute(null, null);

        ArgumentCaptor<List<SamsaraDriverVehicleAssignmentRow>> captor = ArgumentCaptor.captor();
        Mockito.verify(this.samsaraDriverVehicleAssignmentRepository).replaceAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().vehicleId()).isEqualTo("281475");
    }

    @Test
    void execute_assignmentsWithOnlyStartTime_fallsBackToStartTimeForRecencyOrdering() {
        when(this.samsaraFleetClient.fetchDrivers())
                .thenReturn(List.of(new SamsaraDriverWithRaw(activeDriver("41000123"), "{}")));
        DriverVehicleAssignmentV2ObjectResponseBody older = new DriverVehicleAssignmentV2ObjectResponseBody()
                .driver(new GoaDriverTinyResponseResponseBody().id("41000123").name("Jane Trucker"))
                .vehicle(new GoaVehicleTinyResponseResponseBody().id("281474").name("Truck 12"))
                .startTime("2026-07-15T00:00:00Z");
        DriverVehicleAssignmentV2ObjectResponseBody newer = new DriverVehicleAssignmentV2ObjectResponseBody()
                .driver(new GoaDriverTinyResponseResponseBody().id("41000123").name("Jane Trucker"))
                .vehicle(new GoaVehicleTinyResponseResponseBody().id("281475").name("Truck 13"))
                .startTime("2026-07-16T00:00:00Z");
        when(this.samsaraFleetClient.fetchDriverVehicleAssignments(Mockito.anyList()))
                .thenReturn(List.of(newer, older));

        SamsaraDriverSyncTasklet tasklet = new SamsaraDriverSyncTasklet(
                this.samsaraFleetClient, this.samsaraDriverRepository, this.samsaraDriverVehicleAssignmentRepository);

        tasklet.execute(null, null);

        ArgumentCaptor<List<SamsaraDriverVehicleAssignmentRow>> captor = ArgumentCaptor.captor();
        Mockito.verify(this.samsaraDriverVehicleAssignmentRepository).replaceAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().vehicleId()).isEqualTo("281475");
    }

    @Test
    void execute_driverWithNoDynamicAssignment_fallsBackToStaticAssignedVehicle() {
        // This tests the fallback path directly by stubbing fetchDriverVehicleAssignments(List) to return nothing,
        // even though in practice the driverIds+assignmentType=HOS query has been observed to resolve static-only
        // assignments too - see toAssignmentRows's javadoc.
        Driver driver = activeDriver("41000123")
                .staticAssignedVehicle(
                        new DriverStaticAssignedVehicle().id("0895").name("Truck 0895"));
        when(this.samsaraFleetClient.fetchDrivers()).thenReturn(List.of(new SamsaraDriverWithRaw(driver, "{}")));
        when(this.samsaraFleetClient.fetchDriverVehicleAssignments(Mockito.anyList()))
                .thenReturn(List.of());

        SamsaraDriverSyncTasklet tasklet = new SamsaraDriverSyncTasklet(
                this.samsaraFleetClient, this.samsaraDriverRepository, this.samsaraDriverVehicleAssignmentRepository);

        tasklet.execute(null, null);

        ArgumentCaptor<List<SamsaraDriverVehicleAssignmentRow>> captor = ArgumentCaptor.captor();
        Mockito.verify(this.samsaraDriverVehicleAssignmentRepository).replaceAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        SamsaraDriverVehicleAssignmentRow row = captor.getValue().getFirst();
        assertThat(row.driverId()).isEqualTo("41000123");
        assertThat(row.vehicleId()).isEqualTo("0895");
        assertThat(row.vehicleName()).isEqualTo("Truck 0895");
        assertThat(row.startTime()).isNull();
        assertThat(row.assignedAtTime()).isNull();
    }

    @Test
    void execute_driverWithBothDynamicAndStaticAssignment_prefersDynamic() {
        Driver driver = activeDriver("41000123")
                .staticAssignedVehicle(
                        new DriverStaticAssignedVehicle().id("0895").name("Truck 0895"));
        when(this.samsaraFleetClient.fetchDrivers()).thenReturn(List.of(new SamsaraDriverWithRaw(driver, "{}")));
        DriverVehicleAssignmentV2ObjectResponseBody dynamicAssignment =
                new DriverVehicleAssignmentV2ObjectResponseBody()
                        .driver(new GoaDriverTinyResponseResponseBody()
                                .id("41000123")
                                .name("Jane Trucker"))
                        .vehicle(new GoaVehicleTinyResponseResponseBody()
                                .id("281474")
                                .name("Truck 12"))
                        .assignedAtTime("2026-07-16T00:00:00Z");
        when(this.samsaraFleetClient.fetchDriverVehicleAssignments(Mockito.anyList()))
                .thenReturn(List.of(dynamicAssignment));

        SamsaraDriverSyncTasklet tasklet = new SamsaraDriverSyncTasklet(
                this.samsaraFleetClient, this.samsaraDriverRepository, this.samsaraDriverVehicleAssignmentRepository);

        tasklet.execute(null, null);

        ArgumentCaptor<List<SamsaraDriverVehicleAssignmentRow>> captor = ArgumentCaptor.captor();
        Mockito.verify(this.samsaraDriverVehicleAssignmentRepository).replaceAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().vehicleId()).isEqualTo("281474");
    }

    private static Driver activeDriver(String id) {
        return new Driver().id(id).name("Jane Trucker").driverActivationStatus(DriverActivationStatus.ACTIVE);
    }
}
