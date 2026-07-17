package com.lava.swexpedited.batch.samsara;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.lava.swexpedited.samsara.SamsaraDriverWithRaw;
import com.lava.swexpedited.samsara.model.DriverActivationStatus;
import com.lava.swexpedited.samsara.model.DriverVehicleAssignmentV2ObjectResponseBody;
import com.lava.swexpedited.samsara.model.HosClocksForDriver;
import com.lava.swexpedited.samsara.model.HosLogEntry;
import com.lava.swexpedited.samsara.model.VehicleStatsResponseData;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@WireMockTest
class SamsaraFleetClientTest {

    @Test
    void fetchDrivers_singlePage_parsesTypedFieldsAndCapturesRawJson(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/fleet/drivers"))
                .withQueryParam("driverActivationStatus", equalTo("active"))
                .withQueryParam("limit", equalTo("512"))
                .withQueryParam("after", absent())
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [
                                    {
                                      "id": "41000123",
                                      "name": "Jane Trucker",
                                      "username": "jtrucker",
                                      "email": "jane.trucker@example.com",
                                      "phone": "555-0100",
                                      "licenseNumber": "D1234567",
                                      "licenseState": "TX",
                                      "driverActivationStatus": "active",
                                      "tags": [{"id": "1", "name": "expedited"}],
                                      "createdAtTime": "2025-01-01T00:00:00Z",
                                      "updatedAtTime": "2025-06-01T00:00:00Z",
                                      "unmodeledExtraField": "should still show up in rawJson"
                                    }
                                  ],
                                  "pagination": {"endCursor": null, "hasNextPage": false}
                                }
                                """)));

        SamsaraFleetClient client =
                new SamsaraFleetClient(samsaraRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<SamsaraDriverWithRaw> drivers = client.fetchDrivers();

        assertThat(drivers).hasSize(1);
        SamsaraDriverWithRaw driver = drivers.getFirst();
        assertThat(driver.payload().getId()).isEqualTo("41000123");
        assertThat(driver.payload().getName()).isEqualTo("Jane Trucker");
        assertThat(driver.payload().getDriverActivationStatus()).isEqualTo(DriverActivationStatus.ACTIVE);
        assertThat(driver.payload().getTags()).hasSize(1);
        assertThat(driver.payload().getTags().getFirst().getName()).isEqualTo("expedited");
        assertThat(driver.rawJson()).contains("\"unmodeledExtraField\":\"should still show up in rawJson\"");
        assertThat(driver.rawJson()).contains("\"id\":\"41000123\"");
    }

    @Test
    void fetchDrivers_twoPages_passesEndCursorAsAfterParamOnSecondRequest(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/fleet/drivers"))
                .withQueryParam("after", absent())
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [{"id": "1", "name": "Driver One", "driverActivationStatus": "active"}],
                                  "pagination": {"endCursor": "cursor-abc", "hasNextPage": true}
                                }
                                """)));
        stubFor(get(urlPathEqualTo("/fleet/drivers"))
                .withQueryParam("after", equalTo("cursor-abc"))
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [{"id": "2", "name": "Driver Two", "driverActivationStatus": "active"}],
                                  "pagination": {"endCursor": null, "hasNextPage": false}
                                }
                                """)));

        SamsaraFleetClient client =
                new SamsaraFleetClient(samsaraRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<SamsaraDriverWithRaw> drivers = client.fetchDrivers();

        assertThat(drivers).extracting(d -> d.payload().getId()).containsExactly("1", "2");
        verify(1, getRequestedFor(urlPathEqualTo("/fleet/drivers")).withQueryParam("after", equalTo("cursor-abc")));
    }

    @Test
    void fetchDriverVehicleAssignments_singlePage_parsesEntries(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/fleet/driver-vehicle-assignments"))
                .withQueryParam("filterBy", equalTo("drivers"))
                .withQueryParam("driverIds", equalTo("41000123"))
                .withQueryParam("assignmentType", equalTo("HOS"))
                .withQueryParam("limit", equalTo("512"))
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [
                                    {
                                      "driver": {"id": "41000123", "name": "Jane Trucker"},
                                      "vehicle": {"id": "281474", "name": "Truck 12"},
                                      "startTime": "2026-07-01T00:00:00Z",
                                      "endTime": null,
                                      "assignedAtTime": "2026-07-01T00:05:00Z"
                                    }
                                  ],
                                  "pagination": {"endCursor": null, "hasNextPage": false}
                                }
                                """)));

        SamsaraFleetClient client =
                new SamsaraFleetClient(samsaraRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<DriverVehicleAssignmentV2ObjectResponseBody> assignments =
                client.fetchDriverVehicleAssignments(List.of("41000123"));

        assertThat(assignments).hasSize(1);
        assertThat(assignments.getFirst().getDriver().getId()).isEqualTo("41000123");
        assertThat(assignments.getFirst().getVehicle().getName()).isEqualTo("Truck 12");
        assertThat(assignments.getFirst().getStartTime()).isEqualTo("2026-07-01T00:00:00Z");
    }

    @Test
    void fetchDriverVehicleAssignments_emptyDriverIds_returnsEmptyWithoutCallingSamsara(
            WireMockRuntimeInfo wireMockRuntimeInfo) {
        SamsaraFleetClient client =
                new SamsaraFleetClient(samsaraRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<DriverVehicleAssignmentV2ObjectResponseBody> assignments = client.fetchDriverVehicleAssignments(List.of());

        assertThat(assignments).isEmpty();
        verify(0, getRequestedFor(urlPathEqualTo("/fleet/driver-vehicle-assignments")));
    }

    @Test
    void fetchDriverVehicleAssignments_joinsDriverIdsAndOmitsStartAndEndTime(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/fleet/driver-vehicle-assignments"))
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [],
                                  "pagination": {"endCursor": null, "hasNextPage": false}
                                }
                                """)));

        SamsaraFleetClient client =
                new SamsaraFleetClient(samsaraRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        client.fetchDriverVehicleAssignments(List.of("41000123", "41000456"));

        // Samsara silently ignores startTime/endTime once driverIds+assignmentType is present (confirmed against the
        // live API - see SamsaraFleetClient.fetchDriverVehicleAssignments's javadoc), so this method no longer sends
        // them at all.
        verify(getRequestedFor(urlPathEqualTo("/fleet/driver-vehicle-assignments"))
                .withQueryParam("driverIds", equalTo("41000123,41000456"))
                .withQueryParam("assignmentType", equalTo("HOS"))
                .withQueryParam("startTime", absent())
                .withQueryParam("endTime", absent()));
    }

    @Test
    void fetchDriverDutyStatuses_singlePage_parsesEntriesIncludingDisconnectedEmptyStatus(
            WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/fleet/hos/clocks"))
                .withQueryParam("driverIds", equalTo("41000123,41000456"))
                .withQueryParam("limit", equalTo("512"))
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [
                                    {
                                      "driver": {"id": "41000123", "name": "Jane Trucker"},
                                      "currentDutyStatus": {"hosStatusType": "driving"}
                                    },
                                    {
                                      "driver": {"id": "41000456", "name": "John Hauler"},
                                      "currentDutyStatus": {"hosStatusType": ""}
                                    }
                                  ],
                                  "pagination": {"endCursor": null, "hasNextPage": false}
                                }
                                """)));

        SamsaraFleetClient client =
                new SamsaraFleetClient(samsaraRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<HosClocksForDriver> hosClocks = client.fetchDriverDutyStatuses(List.of("41000123", "41000456"));

        assertThat(hosClocks).hasSize(2);
        assertThat(hosClocks.getFirst().getDriver().getId()).isEqualTo("41000123");
        assertThat(hosClocks.getFirst().getCurrentDutyStatus().getHosStatusType())
                .isEqualTo("driving");
        assertThat(hosClocks.getLast().getCurrentDutyStatus().getHosStatusType())
                .isEmpty();
    }

    @Test
    void fetchDriverDutyStatuses_emptyDriverIds_returnsEmptyWithoutCallingSamsara(
            WireMockRuntimeInfo wireMockRuntimeInfo) {
        SamsaraFleetClient client =
                new SamsaraFleetClient(samsaraRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<HosClocksForDriver> hosClocks = client.fetchDriverDutyStatuses(List.of());

        assertThat(hosClocks).isEmpty();
        verify(0, getRequestedFor(urlPathEqualTo("/fleet/hos/clocks")));
    }

    @Test
    void fetchVehicleLocations_singlePage_parsesEntries(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/fleet/vehicles/stats"))
                .withQueryParam("types", equalTo("gps"))
                .withQueryParam("limit", equalTo("512"))
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [
                                    {
                                      "id": "281474",
                                      "name": "Truck 12",
                                      "gps": {
                                        "latitude": 32.735,
                                        "longitude": -97.108,
                                        "headingDegrees": 180.5,
                                        "speedMilesPerHour": 62.3,
                                        "time": "2026-07-16T12:00:00Z",
                                        "reverseGeo": {"formattedLocation": "Fort Worth, TX"}
                                      }
                                    }
                                  ],
                                  "pagination": {"endCursor": null, "hasNextPage": false}
                                }
                                """)));

        SamsaraFleetClient client =
                new SamsaraFleetClient(samsaraRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<VehicleStatsResponseData> locations = client.fetchVehicleLocations();

        assertThat(locations).hasSize(1);
        assertThat(locations.getFirst().getId()).isEqualTo("281474");
        assertThat(locations.getFirst().getGps().getLatitude()).isEqualTo(32.735);
        assertThat(locations.getFirst().getGps().getReverseGeo().getFormattedLocation())
                .isEqualTo("Fort Worth, TX");
    }

    @Test
    void fetchVehicleLocations_retriesOn5xxThenSucceeds(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/fleet/vehicles/stats"))
                .inScenario("locations-retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        stubFor(get(urlPathEqualTo("/fleet/vehicles/stats"))
                .inScenario("locations-retry")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [],
                                  "pagination": {"endCursor": null, "hasNextPage": false}
                                }
                                """)));

        SamsaraFleetClient client =
                new SamsaraFleetClient(samsaraRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<VehicleStatsResponseData> locations = client.fetchVehicleLocations();

        assertThat(locations).isEmpty();
        verify(2, getRequestedFor(urlPathEqualTo("/fleet/vehicles/stats")));
    }

    @Test
    void fetchVehicleLocation_singleVehicle_scopesRequestToThatVehicle(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/fleet/vehicles/stats"))
                .withQueryParam("types", equalTo("gps"))
                .withQueryParam("vehicleIds", equalTo("281474"))
                .withQueryParam("limit", equalTo("512"))
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [
                                    {
                                      "id": "281474",
                                      "name": "Truck 12",
                                      "gps": {
                                        "latitude": 32.735,
                                        "longitude": -97.108,
                                        "headingDegrees": 180.5,
                                        "speedMilesPerHour": 62.3,
                                        "time": "2026-07-16T12:00:00Z",
                                        "reverseGeo": {"formattedLocation": "Fort Worth, TX"}
                                      }
                                    }
                                  ],
                                  "pagination": {"endCursor": null, "hasNextPage": false}
                                }
                                """)));

        SamsaraFleetClient client =
                new SamsaraFleetClient(samsaraRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<VehicleStatsResponseData> locations = client.fetchVehicleLocation("281474");

        assertThat(locations).hasSize(1);
        assertThat(locations.getFirst().getId()).isEqualTo("281474");
        assertThat(locations.getFirst().getGps().getLatitude()).isEqualTo(32.735);
    }

    @Test
    void fetchDriverHosLogs_singlePage_parsesEntriesAndOmitsLimitParam(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/fleet/hos/logs"))
                .withQueryParam("driverIds", equalTo("41000123"))
                .withQueryParam("startTime", equalTo("2026-07-15T12:00:00Z"))
                .withQueryParam("endTime", equalTo("2026-07-16T12:00:00Z"))
                .withQueryParam("limit", absent())
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [
                                    {
                                      "driver": {"id": "41000123", "name": "Jane Trucker"},
                                      "hosLogs": [
                                        {
                                          "hosStatusType": "driving",
                                          "logStartTime": "2026-07-16T11:04:00Z",
                                          "logEndTime": null,
                                          "logRecordedLocation": {"latitude": 27.9, "longitude": -81.6},
                                          "remark": null
                                        }
                                      ]
                                    }
                                  ],
                                  "pagination": {"endCursor": null, "hasNextPage": false}
                                }
                                """)));

        SamsaraFleetClient client =
                new SamsaraFleetClient(samsaraRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<HosLogEntry> hosLogs = client.fetchDriverHosLogs(
                "41000123", Instant.parse("2026-07-15T12:00:00Z"), Instant.parse("2026-07-16T12:00:00Z"));

        assertThat(hosLogs).hasSize(1);
        assertThat(hosLogs.getFirst().getHosStatusType()).isEqualTo("driving");
        assertThat(hosLogs.getFirst().getLogStartTime()).isEqualTo("2026-07-16T11:04:00Z");
        assertThat(hosLogs.getFirst().getLogRecordedLocation().getLatitude()).isEqualTo(27.9);
    }

    private RestClient samsaraRestClient(WireMockRuntimeInfo wireMockRuntimeInfo) {
        return RestClient.builder()
                .baseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
                .build();
    }
}
