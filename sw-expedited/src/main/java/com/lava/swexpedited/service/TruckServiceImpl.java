package com.lava.swexpedited.service;

import com.lava.swexpedited.repository.SamsaraVehicleDiagnosticsRepository;
import com.lava.swexpedited.repository.SamsaraVehicleLocationRepository;
import com.lava.swexpedited.repository.VektorDriverRepository;
import com.lava.swexpedited.repository.VektorTrailerRepository;
import com.lava.swexpedited.repository.VektorTruckRepository;
import com.lava.swexpedited.samsara.SamsaraVehicleDiagnosticsRow;
import com.lava.swexpedited.samsara.SamsaraVehicleLocationRow;
import com.lava.swexpedited.truck.TruckDetailResponse;
import com.lava.swexpedited.truck.TruckListingRow;
import com.lava.swexpedited.vektor.VektorDriverRow;
import com.lava.swexpedited.vektor.VektorTrailerRow;
import com.lava.swexpedited.vektor.VektorTruckRow;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Joins vektor_truck with vektor_driver/vektor_trailer in Java, same convention as {@link SamsaraDriverServiceImpl} -
 * {@code current_driver_id}/{@code current_trailer_id} are Vektor's own best-effort UUIDs with no FK constraint (see
 * {@code VektorTruckRow}'s javadoc), so a null resolved name/label is a normal "unassigned or stale id" state, not an
 * error. Truck detail additionally joins samsara_vehicle_diagnostics/samsara_vehicle_location by
 * {@code matched_samsara_vehicle_id} the same best-effort way - see {@code VinMatchingTruckMatchStrategy}.
 */
@Service
@Transactional(readOnly = true)
public class TruckServiceImpl implements TruckService {

    private static final double METERS_PER_MILE = 1609.344;
    private static final double SECONDS_PER_HOUR = 3600.0;

    private final VektorTruckRepository vektorTruckRepository;
    private final VektorDriverRepository vektorDriverRepository;
    private final VektorTrailerRepository vektorTrailerRepository;
    private final SamsaraVehicleDiagnosticsRepository samsaraVehicleDiagnosticsRepository;
    private final SamsaraVehicleLocationRepository samsaraVehicleLocationRepository;

    public TruckServiceImpl(
            VektorTruckRepository vektorTruckRepository,
            VektorDriverRepository vektorDriverRepository,
            VektorTrailerRepository vektorTrailerRepository,
            SamsaraVehicleDiagnosticsRepository samsaraVehicleDiagnosticsRepository,
            SamsaraVehicleLocationRepository samsaraVehicleLocationRepository) {
        this.vektorTruckRepository = vektorTruckRepository;
        this.vektorDriverRepository = vektorDriverRepository;
        this.vektorTrailerRepository = vektorTrailerRepository;
        this.samsaraVehicleDiagnosticsRepository = samsaraVehicleDiagnosticsRepository;
        this.samsaraVehicleLocationRepository = samsaraVehicleLocationRepository;
    }

    @Override
    public List<TruckListingRow> findAll() {
        Map<String, String> driverNameById = this.vektorDriverRepository.findAll().stream()
                .collect(Collectors.toMap(VektorDriverRow::id, VektorDriverRow::fullName));
        Map<String, String> trailerLabelById = this.vektorTrailerRepository.findAll().stream()
                .collect(Collectors.toMap(VektorTrailerRow::id, VektorTrailerRow::label));

        return this.vektorTruckRepository.findAll().stream()
                .map(truck -> new TruckListingRow(
                        truck.id(),
                        truck.truckNumber(),
                        truck.statusCode(),
                        Optional.ofNullable(truck.currentDriverId())
                                .map(driverNameById::get)
                                .orElse(null),
                        Optional.ofNullable(truck.currentTrailerId())
                                .map(trailerLabelById::get)
                                .orElse(null)))
                .toList();
    }

    @Override
    public Optional<TruckDetailResponse> findDetail(String truckId) {
        return this.vektorTruckRepository.findById(truckId).map(this::toDetailResponse);
    }

    private TruckDetailResponse toDetailResponse(VektorTruckRow truck) {
        String currentDriverName = Optional.ofNullable(truck.currentDriverId())
                .flatMap(this.vektorDriverRepository::findById)
                .map(VektorDriverRow::fullName)
                .orElse(null);
        String currentTrailerLabel = Optional.ofNullable(truck.currentTrailerId())
                .flatMap(this.vektorTrailerRepository::findById)
                .map(VektorTrailerRow::label)
                .orElse(null);

        SamsaraVehicleDiagnosticsRow diagnostics = Optional.ofNullable(truck.matchedSamsaraVehicleId())
                .flatMap(this.samsaraVehicleDiagnosticsRepository::findByVehicleId)
                .orElse(null);
        SamsaraVehicleLocationRow location = Optional.ofNullable(truck.matchedSamsaraVehicleId())
                .flatMap(this.samsaraVehicleLocationRepository::findByVehicleId)
                .orElse(null);

        return new TruckDetailResponse(
                truck.id(),
                truck.truckNumber(),
                truck.statusCode(),
                truck.vin(),
                truck.make(),
                truck.model(),
                truck.year(),
                currentDriverName,
                currentTrailerLabel,
                truck.syncedAt(),
                diagnostics != null ? diagnostics.fuelPercent() : null,
                diagnostics != null ? metersToMiles(diagnostics.odometerMeters()) : null,
                diagnostics != null ? secondsToHours(diagnostics.engineSeconds()) : null,
                diagnostics != null ? diagnostics.faultCodes() : null,
                diagnostics != null ? diagnostics.engineState() : null,
                diagnostics != null ? milliToBase(diagnostics.defLevelMilliPercent()) : null,
                diagnostics != null ? milliToBase(diagnostics.batteryMilliVolts()) : null,
                diagnostics != null ? milliCelsiusToFahrenheit(diagnostics.coolantTempMilliC()) : null,
                diagnostics != null ? diagnostics.engineRpm() : null,
                diagnostics != null ? diagnostics.engineLoadPercent() : null,
                location != null ? location.latitude() : null,
                location != null ? location.longitude() : null,
                location != null ? location.formattedLocation() : null,
                location != null ? location.locationTime() : null);
    }

    private static @Nullable Double metersToMiles(@Nullable Long meters) {
        return meters == null ? null : meters / METERS_PER_MILE;
    }

    private static @Nullable Double secondsToHours(@Nullable Long seconds) {
        return seconds == null ? null : seconds / SECONDS_PER_HOUR;
    }

    private static @Nullable Double milliToBase(@Nullable Integer milliUnits) {
        return milliUnits == null ? null : milliUnits / 1000.0;
    }

    private static @Nullable Double milliCelsiusToFahrenheit(@Nullable Integer milliCelsius) {
        if (milliCelsius == null) {
            return null;
        }
        double celsius = milliCelsius / 1000.0;
        return celsius * 9.0 / 5.0 + 32.0;
    }
}
