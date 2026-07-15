package com.lava.swexpedited.repository;

import static com.lava.swexpedited.model.database.Tables.SHIPMENT_DETAIL;

import com.lava.swexpedited.model.database.tables.pojos.ShipmentDetail;
import com.lava.swexpedited.model.database.tables.records.ShipmentDetailRecord;
import com.lava.swexpedited.shipment.ShipmentDetailRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep12;
import org.jooq.JSON;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class ShipmentDetailRepositoryImpl implements ShipmentDetailRepository {

    private final DSLContext dsl;

    public ShipmentDetailRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void insertAll(List<ShipmentDetailRow> rows) {
        if (rows.isEmpty()) {
            return;
        }

        LocalDateTime syncedAt = LocalDateTime.now();
        InsertValuesStep12<
                        ShipmentDetailRecord,
                        Long,
                        BigDecimal,
                        BigDecimal,
                        BigDecimal,
                        String,
                        String,
                        String,
                        String,
                        String,
                        String,
                        JSON,
                        LocalDateTime>
                insert = this.dsl.insertInto(
                        SHIPMENT_DETAIL,
                        SHIPMENT_DETAIL.OFFER_ID,
                        SHIPMENT_DETAIL.TOTAL_AMOUNT,
                        SHIPMENT_DETAIL.LINE_HAUL_COST,
                        SHIPMENT_DETAIL.RATE_USED,
                        SHIPMENT_DETAIL.SCAC,
                        SHIPMENT_DETAIL.SCAC_NAME,
                        SHIPMENT_DETAIL.TENDER_NUMBER,
                        SHIPMENT_DETAIL.EQUIPMENT_DESC,
                        SHIPMENT_DETAIL.REQUESTOR_NAME,
                        SHIPMENT_DETAIL.REQUESTOR_EMAIL,
                        SHIPMENT_DETAIL.RAW_RESPONSE,
                        SHIPMENT_DETAIL.SYNCED_AT);

        for (ShipmentDetailRow row : rows) {
            insert.values(
                    row.offerId(),
                    row.totalAmount(),
                    row.lineHaulCost(),
                    row.rateUsed(),
                    row.scac(),
                    row.scacName(),
                    row.tenderNumber(),
                    row.equipmentDesc(),
                    row.requestorName(),
                    row.requestorEmail(),
                    JSON.valueOf(row.rawResponse()),
                    syncedAt);
        }

        insert.execute();
    }

    @Override
    public Optional<ShipmentDetailRow> findByOfferId(long offerId) {
        return this.dsl
                .selectFrom(SHIPMENT_DETAIL)
                .where(SHIPMENT_DETAIL.OFFER_ID.eq(offerId))
                .fetchOptionalInto(ShipmentDetail.class)
                .map(row -> new ShipmentDetailRow(
                        row.offerId(),
                        row.totalAmount(),
                        row.lineHaulCost(),
                        row.rateUsed(),
                        row.scac(),
                        row.scacName(),
                        row.tenderNumber(),
                        row.equipmentDesc(),
                        row.requestorName(),
                        row.requestorEmail(),
                        row.rawResponse().data(),
                        row.syncedAt()));
    }
}
