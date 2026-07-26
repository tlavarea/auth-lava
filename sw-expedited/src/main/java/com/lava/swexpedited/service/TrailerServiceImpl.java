package com.lava.swexpedited.service;

import com.lava.swexpedited.repository.VektorTrailerRepository;
import com.lava.swexpedited.repository.VektorTruckRepository;
import com.lava.swexpedited.trailer.TrailerDetailResponse;
import com.lava.swexpedited.trailer.TrailerListingRow;
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
 * truck currently has this trailer" only exists as the forward pointer on the truck side.
 */
@Service
@Transactional(readOnly = true)
public class TrailerServiceImpl implements TrailerService {

    private final VektorTrailerRepository vektorTrailerRepository;
    private final VektorTruckRepository vektorTruckRepository;

    public TrailerServiceImpl(
            VektorTrailerRepository vektorTrailerRepository, VektorTruckRepository vektorTruckRepository) {
        this.vektorTrailerRepository = vektorTrailerRepository;
        this.vektorTruckRepository = vektorTruckRepository;
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
        return this.vektorTrailerRepository
                .findById(trailerId)
                .map(trailer -> new TrailerDetailResponse(
                        trailer.id(), trailer.label(), trailer.manufacturer(), trailer.year(), trailer.syncedAt()));
    }
}
