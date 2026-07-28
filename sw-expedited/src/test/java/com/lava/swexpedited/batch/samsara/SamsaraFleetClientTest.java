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
import com.lava.swexpedited.samsara.SamsaraSafetyEvent;
import com.lava.swexpedited.samsara.SamsaraTrailerWithRaw;
import com.lava.swexpedited.samsara.SamsaraVehicleWithRaw;
import com.lava.swexpedited.samsara.model.DriverActivationStatus;
import com.lava.swexpedited.samsara.model.DriverVehicleAssignmentV2ObjectResponseBody;
import com.lava.swexpedited.samsara.model.HosClocksForDriver;
import com.lava.swexpedited.samsara.model.HosLogEntry;
import com.lava.swexpedited.samsara.model.VehicleStatsGps;
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

    @Test
    void fetchVehicles_singlePage_parsesTypedFieldsAndCapturesRawJson(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/fleet/vehicles"))
                .withQueryParam("limit", equalTo("512"))
                .withQueryParam("after", absent())
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [
                                    {
                                      "id": "281474",
                                      "vin": "1XPBD49X7ND764317",
                                      "name": "2203",
                                      "make": "PETERBILT",
                                      "model": "579",
                                      "year": "2022",
                                      "licensePlate": "AN02697",
                                      "unmodeledExtraField": "should still show up in rawJson"
                                    }
                                  ],
                                  "pagination": {"endCursor": null, "hasNextPage": false}
                                }
                                """)));

        SamsaraFleetClient client =
                new SamsaraFleetClient(samsaraRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<SamsaraVehicleWithRaw> vehicles = client.fetchVehicles();

        assertThat(vehicles).hasSize(1);
        SamsaraVehicleWithRaw vehicle = vehicles.getFirst();
        assertThat(vehicle.payload().getId()).isEqualTo("281474");
        assertThat(vehicle.payload().getVin()).isEqualTo("1XPBD49X7ND764317");
        assertThat(vehicle.payload().getName()).isEqualTo("2203");
        assertThat(vehicle.payload().getMake()).isEqualTo("PETERBILT");
        assertThat(vehicle.payload().getModel()).isEqualTo("579");
        assertThat(vehicle.payload().getYear()).isEqualTo("2022");
        assertThat(vehicle.payload().getLicensePlate()).isEqualTo("AN02697");
        assertThat(vehicle.rawJson()).contains("\"unmodeledExtraField\":\"should still show up in rawJson\"");
    }

    @Test
    void fetchVehicles_twoPages_passesEndCursorAsAfterParamOnSecondRequest(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/fleet/vehicles"))
                .withQueryParam("after", absent())
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [{"id": "1", "vin": "VIN1", "name": "Truck One"}],
                                  "pagination": {"endCursor": "cursor-abc", "hasNextPage": true}
                                }
                                """)));
        stubFor(get(urlPathEqualTo("/fleet/vehicles"))
                .withQueryParam("after", equalTo("cursor-abc"))
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [{"id": "2", "vin": "VIN2", "name": "Truck Two"}],
                                  "pagination": {"endCursor": null, "hasNextPage": false}
                                }
                                """)));

        SamsaraFleetClient client =
                new SamsaraFleetClient(samsaraRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<SamsaraVehicleWithRaw> vehicles = client.fetchVehicles();

        assertThat(vehicles).extracting(v -> v.payload().getId()).containsExactly("1", "2");
        verify(1, getRequestedFor(urlPathEqualTo("/fleet/vehicles")).withQueryParam("after", equalTo("cursor-abc")));
    }

    @Test
    void fetchTrailers_singlePage_parsesTypedFieldsAndCapturesRawJson(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/fleet/trailers"))
                .withQueryParam("limit", equalTo("512"))
                .withQueryParam("after", absent())
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [
                                    {
                                      "id": "112",
                                      "name": "1704",
                                      "licensePlate": "34A1W4",
                                      "trailerSerialNumber": "SN-112",
                                      "externalIds": {"samsara.vin": "5MC125315H5165489"},
                                      "unmodeledExtraField": "should still show up in rawJson"
                                    }
                                  ],
                                  "pagination": {"endCursor": null, "hasNextPage": false}
                                }
                                """)));

        SamsaraFleetClient client =
                new SamsaraFleetClient(samsaraRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<SamsaraTrailerWithRaw> trailers = client.fetchTrailers();

        assertThat(trailers).hasSize(1);
        SamsaraTrailerWithRaw trailer = trailers.getFirst();
        assertThat(trailer.payload().getId()).isEqualTo("112");
        assertThat(trailer.payload().getName()).isEqualTo("1704");
        assertThat(trailer.payload().getLicensePlate()).isEqualTo("34A1W4");
        assertThat(trailer.payload().getTrailerSerialNumber()).isEqualTo("SN-112");
        assertThat(trailer.payload().getExternalIds()).containsEntry("samsara.vin", "5MC125315H5165489");
        assertThat(trailer.rawJson()).contains("\"unmodeledExtraField\":\"should still show up in rawJson\"");
    }

    @Test
    void fetchTrailers_twoPages_passesEndCursorAsAfterParamOnSecondRequest(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/fleet/trailers"))
                .withQueryParam("after", absent())
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [{"id": "1", "name": "Trailer One"}],
                                  "pagination": {"endCursor": "cursor-abc", "hasNextPage": true}
                                }
                                """)));
        stubFor(get(urlPathEqualTo("/fleet/trailers"))
                .withQueryParam("after", equalTo("cursor-abc"))
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [{"id": "2", "name": "Trailer Two"}],
                                  "pagination": {"endCursor": null, "hasNextPage": false}
                                }
                                """)));

        SamsaraFleetClient client =
                new SamsaraFleetClient(samsaraRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<SamsaraTrailerWithRaw> trailers = client.fetchTrailers();

        assertThat(trailers).extracting(t -> t.payload().getId()).containsExactly("1", "2");
        verify(1, getRequestedFor(urlPathEqualTo("/fleet/trailers")).withQueryParam("after", equalTo("cursor-abc")));
    }

    @Test
    void fetchVehicleDiagnostics_mergesResultsFromEachGroupedTypesCallByVehicleId(
            WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/fleet/vehicles/stats"))
                .withQueryParam("types", equalTo("fuelPercents,obdOdometerMeters,obdEngineSeconds,faultCodes"))
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [
                                    {
                                      "id": "281474",
                                      "name": "Truck 12",
                                      "fuelPercent": {"time": "2026-07-16T12:00:00Z", "value": 62},
                                      "obdOdometerMeters": {"time": "2026-07-16T12:00:00Z", "value": 296451840},
                                      "obdEngineSeconds": {"time": "2026-07-16T12:00:00Z", "value": 19483200},
                                      "faultCodes": {"canBusType": "CANBUS_J1939_500"}
                                    }
                                  ],
                                  "pagination": {"endCursor": null, "hasNextPage": false}
                                }
                                """)));
        stubFor(get(urlPathEqualTo("/fleet/vehicles/stats"))
                .withQueryParam(
                        "types",
                        equalTo("engineStates,defLevelMilliPercent,batteryMilliVolts,engineCoolantTemperatureMilliC"))
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [
                                    {
                                      "id": "281474",
                                      "name": "Truck 12",
                                      "engineState": {"time": "2026-07-16T12:00:00Z", "value": "On"},
                                      "defLevelMilliPercent": {"time": "2026-07-16T12:00:00Z", "value": 41000},
                                      "batteryMilliVolts": {"time": "2026-07-16T12:00:00Z", "value": 13200},
                                      "engineCoolantTemperatureMilliC": {"time": "2026-07-16T12:00:00Z", "value": 92220}
                                    }
                                  ],
                                  "pagination": {"endCursor": null, "hasNextPage": false}
                                }
                                """)));
        stubFor(get(urlPathEqualTo("/fleet/vehicles/stats"))
                .withQueryParam("types", equalTo("engineRpm,engineLoadPercent,ecuSpeedMph"))
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [
                                    {
                                      "id": "281474",
                                      "name": "Truck 12",
                                      "engineRpm": {"time": "2026-07-16T12:00:00Z", "value": 1200},
                                      "engineLoadPercent": {"time": "2026-07-16T12:00:00Z", "value": 54},
                                      "ecuSpeedMph": {"time": "2026-07-16T12:00:00Z", "value": 62.5}
                                    }
                                  ],
                                  "pagination": {"endCursor": null, "hasNextPage": false}
                                }
                                """)));

        SamsaraFleetClient client =
                new SamsaraFleetClient(samsaraRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<VehicleStatsResponseData> diagnostics = client.fetchVehicleDiagnostics();

        assertThat(diagnostics).hasSize(1);
        VehicleStatsResponseData merged = diagnostics.getFirst();
        assertThat(merged.getId()).isEqualTo("281474");
        assertThat(merged.getFuelPercent().getValue()).isEqualTo(62L);
        assertThat(merged.getObdOdometerMeters().getValue()).isEqualTo(296451840L);
        assertThat(merged.getObdEngineSeconds().getValue()).isEqualTo(19483200L);
        assertThat(merged.getFaultCodes()).isNotNull();
        assertThat(merged.getEngineState().getValue().getValue()).isEqualTo("On");
        assertThat(merged.getDefLevelMilliPercent().getValue()).isEqualTo(41000L);
        assertThat(merged.getBatteryMilliVolts().getValue()).isEqualTo(13200L);
        assertThat(merged.getEngineCoolantTemperatureMilliC().getValue()).isEqualTo(92220L);
        assertThat(merged.getEngineRpm().getValue()).isEqualTo(1200L);
        assertThat(merged.getEngineLoadPercent().getValue()).isEqualTo(54L);
        assertThat(merged.getEcuSpeedMph().getValue()).isEqualTo(62.5);

        verify(
                1,
                getRequestedFor(urlPathEqualTo("/fleet/vehicles/stats"))
                        .withQueryParam(
                                "types", equalTo("fuelPercents,obdOdometerMeters,obdEngineSeconds,faultCodes")));
        verify(
                1,
                getRequestedFor(urlPathEqualTo("/fleet/vehicles/stats"))
                        .withQueryParam(
                                "types",
                                equalTo(
                                        "engineStates,defLevelMilliPercent,batteryMilliVolts,engineCoolantTemperatureMilliC")));
        verify(
                1,
                getRequestedFor(urlPathEqualTo("/fleet/vehicles/stats"))
                        .withQueryParam("types", equalTo("engineRpm,engineLoadPercent,ecuSpeedMph")));
    }

    @Test
    void fetchVehicleGpsHistory_singlePage_parsesFlattenedGpsPoints(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/fleet/vehicles/stats/history"))
                .withQueryParam("startTime", equalTo("2026-07-27T00:00:00Z"))
                .withQueryParam("endTime", equalTo("2026-07-28T00:00:00Z"))
                .withQueryParam("vehicleIds", equalTo("281474"))
                .withQueryParam("types", equalTo("gps"))
                .withQueryParam("limit", equalTo("512"))
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [
                                    {
                                      "id": "281474",
                                      "name": "Truck 12",
                                      "externalIds": {"samsara.vin": "1XPBD49X7ND764317"},
                                      "gps": [
                                        {
                                          "time": "2026-07-27T12:00:00Z",
                                          "latitude": 32.735,
                                          "longitude": -97.108,
                                          "headingDegrees": 180.5,
                                          "speedMilesPerHour": 62.3,
                                          "isEcuSpeed": true,
                                          "reverseGeo": {"formattedLocation": "Fort Worth, TX"}
                                        },
                                        {
                                          "time": "2026-07-27T12:01:00Z",
                                          "latitude": 32.736,
                                          "longitude": -97.109,
                                          "headingDegrees": 181.0,
                                          "speedMilesPerHour": 0.0,
                                          "isEcuSpeed": true,
                                          "reverseGeo": {"formattedLocation": "Fort Worth, TX"}
                                        }
                                      ]
                                    }
                                  ],
                                  "pagination": {"endCursor": null, "hasNextPage": false}
                                }
                                """)));

        SamsaraFleetClient client =
                new SamsaraFleetClient(samsaraRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<VehicleStatsGps> points = client.fetchVehicleGpsHistory(
                "281474", Instant.parse("2026-07-27T00:00:00Z"), Instant.parse("2026-07-28T00:00:00Z"));

        assertThat(points).hasSize(2);
        assertThat(points.getFirst().getLatitude()).isEqualTo(32.735);
        assertThat(points.getFirst().getReverseGeo().getFormattedLocation()).isEqualTo("Fort Worth, TX");
        assertThat(points.getFirst().getIsEcuSpeed()).isTrue();
        assertThat(points.getLast().getSpeedMilesPerHour()).isEqualTo(0.0);
    }

    @Test
    void fetchVehicleGpsHistory_twoPages_passesEndCursorAsAfterParamOnSecondRequest(
            WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/fleet/vehicles/stats/history"))
                .withQueryParam("after", absent())
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [{"id": "281474", "name": "Truck 12", "gps": [
                                    {"time": "2026-07-27T12:00:00Z", "latitude": 32.735, "longitude": -97.108}
                                  ]}],
                                  "pagination": {"endCursor": "cursor-abc", "hasNextPage": true}
                                }
                                """)));
        stubFor(get(urlPathEqualTo("/fleet/vehicles/stats/history"))
                .withQueryParam("after", equalTo("cursor-abc"))
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [{"id": "281474", "name": "Truck 12", "gps": [
                                    {"time": "2026-07-27T12:01:00Z", "latitude": 32.736, "longitude": -97.109}
                                  ]}],
                                  "pagination": {"endCursor": null, "hasNextPage": false}
                                }
                                """)));

        SamsaraFleetClient client =
                new SamsaraFleetClient(samsaraRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<VehicleStatsGps> points = client.fetchVehicleGpsHistory(
                "281474", Instant.parse("2026-07-27T00:00:00Z"), Instant.parse("2026-07-28T00:00:00Z"));

        assertThat(points).extracting(VehicleStatsGps::getLatitude).containsExactly(32.735, 32.736);
        verify(
                1,
                getRequestedFor(urlPathEqualTo("/fleet/vehicles/stats/history"))
                        .withQueryParam("after", equalTo("cursor-abc")));
    }

    @Test
    void fetchSafetyEvents_singlePage_parsesNestedBehaviorLabelsLocationDriverAndMedia(
            WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/safety-events/stream"))
                .withQueryParam("startTime", equalTo("2026-07-27T00:00:00Z"))
                .withQueryParam("assetIds", equalTo("281474"))
                .withQueryParam("includeDriver", equalTo("true"))
                .withQueryParam("limit", equalTo("512"))
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [
                                    {
                                      "id": "evt-1",
                                      "startMs": 1785312000000,
                                      "behaviorLabels": [{"label": "Harsh Brake", "source": "Camera"}],
                                      "location": {
                                        "latitude": 32.735,
                                        "longitude": -97.108,
                                        "headingDegrees": 180.5,
                                        "accuracyMeters": 5.0,
                                        "address": {
                                          "street": "100 Main St",
                                          "city": "Fort Worth",
                                          "state": "TX",
                                          "postalCode": "76102"
                                        }
                                      },
                                      "driver": {"id": "41000123", "name": "Jane Trucker"},
                                      "media": [{"input": "dashcamRoadFacing", "url": "https://example.com/clip.mp4"}],
                                      "incidentReportUrl": "https://cloud.samsara.com/report/evt-1"
                                    }
                                  ],
                                  "pagination": {"endCursor": null, "hasNextPage": false}
                                }
                                """)));

        SamsaraFleetClient client =
                new SamsaraFleetClient(samsaraRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        List<SamsaraSafetyEvent> events = client.fetchSafetyEvents("281474", Instant.parse("2026-07-27T00:00:00Z"));

        assertThat(events).hasSize(1);
        SamsaraSafetyEvent event = events.getFirst();
        assertThat(event.id()).isEqualTo("evt-1");
        assertThat(event.startMs()).isEqualTo(1785312000000L);
        assertThat(event.behaviorLabels()).hasSize(1);
        assertThat(event.behaviorLabels().getFirst().label()).isEqualTo("Harsh Brake");
        assertThat(event.location().latitude()).isEqualTo(32.735);
        assertThat(event.location().address().city()).isEqualTo("Fort Worth");
        assertThat(event.driver().name()).isEqualTo("Jane Trucker");
        assertThat(event.media().getFirst().url()).isEqualTo("https://example.com/clip.mp4");
        assertThat(event.incidentReportUrl()).isEqualTo("https://cloud.samsara.com/report/evt-1");
    }

    @Test
    void fetchSafetyEvents_omitsEndTimeParam(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/safety-events/stream"))
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "data": [],
                                  "pagination": {"endCursor": null, "hasNextPage": false}
                                }
                                """)));

        SamsaraFleetClient client =
                new SamsaraFleetClient(samsaraRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        client.fetchSafetyEvents("281474", Instant.parse("2026-07-27T00:00:00Z"));

        verify(getRequestedFor(urlPathEqualTo("/safety-events/stream")).withQueryParam("endTime", absent()));
    }

    private RestClient samsaraRestClient(WireMockRuntimeInfo wireMockRuntimeInfo) {
        return RestClient.builder()
                .baseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
                .build();
    }
}
