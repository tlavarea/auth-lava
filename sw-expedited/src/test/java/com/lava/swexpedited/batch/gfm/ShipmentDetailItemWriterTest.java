package com.lava.swexpedited.batch.gfm;

import static org.mockito.Mockito.verify;

import com.lava.swexpedited.repository.ShipmentDetailRepository;
import com.lava.swexpedited.shipment.ShipmentDetailRow;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.Chunk;

@ExtendWith(MockitoExtension.class)
class ShipmentDetailItemWriterTest {

    @Mock
    private ShipmentDetailRepository shipmentDetailRepository;

    @Test
    void write_insertsAllItemsInChunk() {
        ShipmentDetailRow row =
                new ShipmentDetailRow(1284311010L, null, null, null, null, null, null, null, null, null, "{}", null);
        ShipmentDetailItemWriter writer = new ShipmentDetailItemWriter(shipmentDetailRepository);

        writer.write(new Chunk<>(List.of(row)));

        verify(shipmentDetailRepository).insertAll(List.of(row));
    }
}
