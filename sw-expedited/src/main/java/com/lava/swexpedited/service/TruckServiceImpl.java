package com.lava.swexpedited.service;

import com.lava.swexpedited.repository.VektorDriverRepository;
import com.lava.swexpedited.repository.VektorTrailerRepository;
import com.lava.swexpedited.repository.VektorTruckRepository;
import com.lava.swexpedited.truck.TruckDetailResponse;
import com.lava.swexpedited.truck.TruckListingRow;
import com.lava.swexpedited.vektor.VektorDriverRow;
import com.lava.swexpedited.vektor.VektorTrailerRow;
import com.lava.swexpedited.vektor.VektorTruckRow;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Joins vektor_truck with vektor_driver/vektor_trailer in Java, same convention as {@link SamsaraDriverServiceImpl} -
 * {@code current_driver_id}/{@code current_trailer_id} are Vektor's own best-effort UUIDs with no FK constraint (see
 * {@code VektorTruckRow}'s javadoc), so a null resolved name/label is a normal "unassigned or stale id" state, not an
 * error.
 */
@Service
@Transactional(readOnly = true)
public class TruckServiceImpl implements TruckService {

    private final VektorTruckRepository vektorTruckRepository;
    private final VektorDriverRepository vektorDriverRepository;
    private final VektorTrailerRepository vektorTrailerRepository;

    public TruckServiceImpl(
            VektorTruckRepository vektorTruckRepository,
            VektorDriverRepository vektorDriverRepository,
            VektorTrailerRepository vektorTrailerRepository) {
        this.vektorTruckRepository = vektorTruckRepository;
        this.vektorDriverRepository = vektorDriverRepository;
        this.vektorTrailerRepository = vektorTrailerRepository;
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
                truck.syncedAt());
    }
}
