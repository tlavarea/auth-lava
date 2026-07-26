package com.lava.swexpedited.controller;

import com.lava.swexpedited.service.TruckService;
import com.lava.swexpedited.truck.TruckDetailResponse;
import com.lava.swexpedited.truck.TruckListingRow;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TruckController {

    private final TruckService truckService;

    public TruckController(TruckService truckService) {
        this.truckService = truckService;
    }

    @GetMapping("/api/trucks")
    public List<TruckListingRow> trucks() {
        return this.truckService.findAll();
    }

    @GetMapping("/api/trucks/{truckId}")
    public ResponseEntity<TruckDetailResponse> truck(@PathVariable String truckId) {
        return this.truckService
                .findDetail(truckId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
