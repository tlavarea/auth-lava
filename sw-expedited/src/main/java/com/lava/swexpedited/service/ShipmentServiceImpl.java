package com.lava.swexpedited.service;

import com.lava.swexpedited.repository.ShipmentDetailRepository;
import com.lava.swexpedited.repository.ShipmentListingRepository;
import com.lava.swexpedited.shipment.ShipmentDetailResponse;
import com.lava.swexpedited.shipment.ShipmentDetailRow;
import com.lava.swexpedited.shipment.ShipmentListingRow;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ShipmentServiceImpl implements ShipmentService {

    private final ShipmentListingRepository shipmentListingRepository;
    private final ShipmentDetailRepository shipmentDetailRepository;

    public ShipmentServiceImpl(
            ShipmentListingRepository shipmentListingRepository, ShipmentDetailRepository shipmentDetailRepository) {
        this.shipmentListingRepository = shipmentListingRepository;
        this.shipmentDetailRepository = shipmentDetailRepository;
    }

    @Override
    public List<ShipmentListingRow> findAll() {
        return shipmentListingRepository.findAll();
    }

    @Override
    public Optional<ShipmentDetailResponse> findDetail(long offerId) {
        return shipmentListingRepository.findByOfferId(offerId).map(listing -> {
            ShipmentDetailRow detail = shipmentDetailRepository
                    .findByOfferId(offerId)
                    .orElse(new ShipmentDetailRow(
                            offerId, null, null, null, null, null, null, null, null, null, null, null));
            return new ShipmentDetailResponse(
                    listing,
                    detail.totalAmount(),
                    detail.lineHaulCost(),
                    detail.rateUsed(),
                    detail.scac(),
                    detail.scacName(),
                    detail.tenderNumber(),
                    detail.equipmentDesc(),
                    detail.requestorName(),
                    detail.requestorEmail(),
                    detail.rawResponse(),
                    detail.syncedAt());
        });
    }
}
