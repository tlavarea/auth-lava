package com.lava.swexpedited.controller;

import com.lava.swexpedited.service.ShipmentService;
import com.lava.swexpedited.shipment.OfferResponseRequest;
import com.lava.swexpedited.shipment.ShipmentDetailResponse;
import com.lava.swexpedited.shipment.ShipmentListingRow;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @GetMapping("/api/shipments")
    public List<ShipmentListingRow> shipments() {
        return shipmentService.findAll();
    }

    @GetMapping("/api/shipments/{offerId}")
    public ResponseEntity<ShipmentDetailResponse> shipment(@PathVariable long offerId) {
        return shipmentService
                .findDetail(offerId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/api/shipments/{offerId}/respond")
    public ResponseEntity<Void> respondToOffer(@PathVariable long offerId, @RequestBody OfferResponseRequest request) {
        shipmentService.respondToOffer(offerId, request);
        return ResponseEntity.noContent().build();
    }
}
