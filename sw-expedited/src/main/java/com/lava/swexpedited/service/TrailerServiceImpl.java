package com.lava.swexpedited.service;

import com.lava.swexpedited.repository.SamsaraTrailerRepository;
import com.lava.swexpedited.repository.VektorDriverRepository;
import com.lava.swexpedited.repository.VektorTrailerRepository;
import com.lava.swexpedited.repository.VektorTruckRepository;
import com.lava.swexpedited.samsara.SamsaraTrailerRow;
import com.lava.swexpedited.trailer.TrailerDetailResponse;
import com.lava.swexpedited.trailer.TrailerListingRow;
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
 * Joins vektor_trailer with a reverse lookup over vektor_truck.current_trailer_id, same in-Java-join convention as
 * {@link SamsaraDriverServiceImpl}/{@link TruckServiceImpl} - a trailer has no assignment column of its own, so "which
 * truck currently has this trailer" only exists as the forward pointer on the truck side. The detail view additionally
 * resolves that truck's current driver (for {@code currentDriverName}) and joins samsara_trailer by
 * {@code matched_samsara_trailer_id} (for {@code licensePlate}/{@code assetSerialNumber}) the same best-effort way
 * {@link TruckServiceImpl} joins samsara_vehicle - see {@code VinMatchingTrailerMatchStrategy}.
 */
@Service
@Transactional(readOnly = true)
public class TrailerServiceImpl implements TrailerService {

    private final VektorTrailerRepository vektorTrailerRepository;
    private final VektorTruckRepository vektorTruckRepository;
    private final VektorDriverRepository vektorDriverRepository;
    private final SamsaraTrailerRepository samsaraTrailerRepository;

    public TrailerServiceImpl(
            VektorTrailerRepository vektorTrailerRepository,
            VektorTruckRepository vektorTruckRepository,
            VektorDriverRepository vektorDriverRepository,
            SamsaraTrailerRepository samsaraTrailerRepository) {
        this.vektorTrailerRepository = vektorTrailerRepository;
        this.vektorTruckRepository = vektorTruckRepository;
        this.vektorDriverRepository = vektorDriverRepository;
        this.samsaraTrailerRepository = samsaraTrailerRepository;
    }

    @Override
    public List<TrailerListingRow> findAll() {
        Map<String, String> truckNumberByTrailerId = this.vektorTruckRepository.findAll().stream()
                .filter(truck -> truck.currentTrailerId() != null)
                .collect(Collectors.toMap(VektorTruckRow::currentTrailerId, VektorTruckRow::truckNumber));

        return this.vektorTrailerRepository.findAll().stream()
                .map(trailer -> new TrailerListingRow(
                        trailer.id(),
                        trailer.label(),
                        trailer.manufacturer(),
                        trailer.year(),
                        truckNumberByTrailerId.get(trailer.id())))
                .toList();
    }

    @Override
    public Optional<TrailerDetailResponse> findDetail(String trailerId) {
        return this.vektorTrailerRepository.findById(trailerId).map(this::toDetailResponse);
    }

    private TrailerDetailResponse toDetailResponse(VektorTrailerRow trailer) {
        VektorTruckRow currentTruck = this.vektorTruckRepository.findAll().stream()
                .filter(truck -> trailer.id().equals(truck.currentTrailerId()))
                .findFirst()
                .orElse(null);
        String currentTruckNumber = currentTruck != null ? currentTruck.truckNumber() : null;
        String currentDriverName = currentTruck == null
                ? null
                : Optional.ofNullable(currentTruck.currentDriverId())
                        .flatMap(this.vektorDriverRepository::findById)
                        .map(VektorDriverRow::fullName)
                        .orElse(null);

        SamsaraTrailerRow samsaraTrailer = Optional.ofNullable(trailer.matchedSamsaraTrailerId())
                .flatMap(this.samsaraTrailerRepository::findById)
                .orElse(null);

        return new TrailerDetailResponse(
                trailer.id(),
                trailer.label(),
                trailer.manufacturer(),
                trailer.year(),
                trailer.vin(),
                samsaraTrailer != null ? samsaraTrailer.licensePlate() : null,
                samsaraTrailer != null ? samsaraTrailer.trailerSerialNumber() : null,
                currentTruckNumber,
                currentDriverName,
                trailer.syncedAt());
    }
}
