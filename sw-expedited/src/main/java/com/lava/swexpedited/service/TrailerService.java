package com.lava.swexpedited.service;

import com.lava.swexpedited.trailer.TrailerDetailResponse;
import com.lava.swexpedited.trailer.TrailerListingRow;
import java.util.List;
import java.util.Optional;

public interface TrailerService {

    List<TrailerListingRow> findAll();

    Optional<TrailerDetailResponse> findDetail(String trailerId);
}
