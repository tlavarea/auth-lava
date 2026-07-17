package com.lava.swexpedited.repository;

import static com.lava.swexpedited.model.database.Tables.SHIPMENT_LISTING;

import com.lava.swexpedited.model.database.tables.pojos.ShipmentListing;
import com.lava.swexpedited.model.database.tables.records.ShipmentListingRecord;
import com.lava.swexpedited.shipment.ShipmentListingRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep15;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class ShipmentListingRepositoryImpl implements ShipmentListingRepository {

    private final DSLContext dsl;

    public ShipmentListingRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void replaceAll(List<ShipmentListingRow> rows) {
        this.dsl.deleteFrom(SHIPMENT_LISTING).execute();

        if (rows.isEmpty()) {
            return;
        }

        LocalDateTime syncedAt = LocalDateTime.now();
        InsertValuesStep15<
                        ShipmentListingRecord,
                        Long,
                        String,
                        LocalDateTime,
                        String,
                        String,
                        String,
                        String,
                        String,
                        String,
                        String,
                        Integer,
                        Integer,
                        LocalDate,
                        LocalDate,
                        LocalDateTime>
                insert = this.dsl.insertInto(
                        SHIPMENT_LISTING,
                        SHIPMENT_LISTING.OFFER_ID,
                        SHIPMENT_LISTING.STATUS,
                        SHIPMENT_LISTING.EXPIRATION_DATE,
                        SHIPMENT_LISTING.SHIPMENT_ID,
                        SHIPMENT_LISTING.SHIPMENT_TYPE,
                        SHIPMENT_LISTING.RANK,
                        SHIPMENT_LISTING.GBLOC,
                        SHIPMENT_LISTING.ORIGIN,
                        SHIPMENT_LISTING.DESTINATION,
                        SHIPMENT_LISTING.EQUIP_TYPE,
                        SHIPMENT_LISTING.CONVEYANCES_OFFERED,
                        SHIPMENT_LISTING.CONVEYANCES_ACCEPTED,
                        SHIPMENT_LISTING.PICKUP_DATE,
                        SHIPMENT_LISTING.REQUIRED_DELIVERY_DATE,
                        SHIPMENT_LISTING.SYNCED_AT);

        for (ShipmentListingRow row : rows) {
            insert.values(
                    row.offerId(),
                    row.status(),
                    row.expirationDate(),
                    row.shipmentId(),
                    row.shipmentType(),
                    row.rank(),
                    row.gbloc(),
                    row.origin(),
                    row.destination(),
                    row.equipType(),
                    row.conveyancesOffered(),
                    row.conveyancesAccepted(),
                    row.pickupDate(),
                    row.requiredDeliveryDate(),
                    syncedAt);
        }

        insert.execute();
    }

    @Override
    public List<ShipmentListingRow> findAll() {
        return this.dsl.selectFrom(SHIPMENT_LISTING).fetchInto(ShipmentListing.class).stream()
                .map(this::toRow)
                .toList();
    }

    @Override
    public Optional<ShipmentListingRow> findByOfferId(long offerId) {
        return this.dsl
                .selectFrom(SHIPMENT_LISTING)
                .where(SHIPMENT_LISTING.OFFER_ID.eq(offerId))
                .fetchOptionalInto(ShipmentListing.class)
                .map(this::toRow);
    }

    @Override
    @Transactional
    public void markViablePickups(Set<Long> offerIds) {
        if (offerIds.isEmpty()) {
            return;
        }

        this.dsl
                .update(SHIPMENT_LISTING)
                .set(SHIPMENT_LISTING.VIABLE_PICKUP, true)
                .where(SHIPMENT_LISTING.OFFER_ID.in(offerIds))
                .execute();
    }

    private ShipmentListingRow toRow(ShipmentListing row) {
        return new ShipmentListingRow(
                row.offerId(),
                row.status(),
                row.expirationDate(),
                row.shipmentId(),
                row.shipmentType(),
                row.rank(),
                row.gbloc(),
                row.origin(),
                row.destination(),
                row.equipType(),
                row.conveyancesOffered(),
                row.conveyancesAccepted(),
                row.pickupDate(),
                row.requiredDeliveryDate(),
                row.syncedAt(),
                row.viablePickup());
    }
}
