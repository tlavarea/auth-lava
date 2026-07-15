package com.lava.swexpedited.batch;

import com.lava.swexpedited.repository.ShipmentDetailRepository;
import com.lava.swexpedited.shipment.ShipmentDetailRow;
import java.util.ArrayList;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
public class ShipmentDetailItemWriter implements ItemWriter<ShipmentDetailRow> {

    private final ShipmentDetailRepository shipmentDetailRepository;

    public ShipmentDetailItemWriter(ShipmentDetailRepository shipmentDetailRepository) {
        this.shipmentDetailRepository = shipmentDetailRepository;
    }

    @Override
    public void write(Chunk<? extends ShipmentDetailRow> chunk) {
        shipmentDetailRepository.insertAll(new ArrayList<>(chunk.getItems()));
    }
}
