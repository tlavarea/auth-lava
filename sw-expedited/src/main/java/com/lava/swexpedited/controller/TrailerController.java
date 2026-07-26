package com.lava.swexpedited.controller;

import com.lava.swexpedited.service.TrailerService;
import com.lava.swexpedited.trailer.TrailerDetailResponse;
import com.lava.swexpedited.trailer.TrailerListingRow;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrailerController {

    private final TrailerService trailerService;

    public TrailerController(TrailerService trailerService) {
        this.trailerService = trailerService;
    }

    @GetMapping("/api/trailers")
    public List<TrailerListingRow> trailers() {
        return this.trailerService.findAll();
    }

    @GetMapping("/api/trailers/{trailerId}")
    public ResponseEntity<TrailerDetailResponse> trailer(@PathVariable String trailerId) {
        return this.trailerService
                .findDetail(trailerId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
