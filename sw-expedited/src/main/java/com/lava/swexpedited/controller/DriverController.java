package com.lava.swexpedited.controller;

import com.lava.swexpedited.samsara.DriverDetailResponse;
import com.lava.swexpedited.samsara.DriverListingRow;
import com.lava.swexpedited.service.SamsaraDriverService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DriverController {

    private final SamsaraDriverService samsaraDriverService;

    public DriverController(SamsaraDriverService samsaraDriverService) {
        this.samsaraDriverService = samsaraDriverService;
    }

    @GetMapping("/api/drivers")
    public List<DriverListingRow> drivers() {
        return samsaraDriverService.findAll();
    }

    @GetMapping("/api/drivers/{driverId}")
    public ResponseEntity<DriverDetailResponse> driver(@PathVariable String driverId) {
        return samsaraDriverService
                .findDetail(driverId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
