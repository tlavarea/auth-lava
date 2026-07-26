package com.lava.swexpedited.service;

import com.lava.swexpedited.truck.TruckDetailResponse;
import com.lava.swexpedited.truck.TruckListingRow;
import java.util.List;
import java.util.Optional;

public interface TruckService {

    List<TruckListingRow> findAll();

    Optional<TruckDetailResponse> findDetail(String truckId);
}
