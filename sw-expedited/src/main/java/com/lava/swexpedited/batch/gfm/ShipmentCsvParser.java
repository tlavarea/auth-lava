package com.lava.swexpedited.batch.gfm;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.lava.swexpedited.shipment.ShipmentListingRow;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Maps the GFM current-shipments CSV export (see the sample in the feature's design notes) to domain rows. */
@Component
public class ShipmentCsvParser {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm:ss a");
    private static final Pattern CONVEYANCES_PATTERN = Pattern.compile("\\[\\s*(\\d+)\\s*/\\s*(\\d+)\\s*]");

    private final CsvMapper csvMapper = new CsvMapper();
    private final CsvSchema csvSchema = CsvSchema.emptySchema().withHeader();

    public List<ShipmentListingRow> parse(byte[] csv) {
        try (MappingIterator<Map<String, String>> rows =
                this.csvMapper.readerFor(Map.class).with(this.csvSchema).readValues(csv)) {
            List<ShipmentListingRow> parsed = new ArrayList<>();

            while (rows.hasNext()) {
                parsed.add(toRow(rows.next()));
            }

            return parsed;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse shipment CSV", e);
        }
    }

    private ShipmentListingRow toRow(Map<String, String> columns) {
        String conveyancesRaw = columns.get("Conveyances Offered/Accepted");
        Matcher conveyances = CONVEYANCES_PATTERN.matcher(conveyancesRaw);

        if (!conveyances.matches()) {
            throw new IllegalArgumentException("Unrecognized conveyances format: " + conveyancesRaw);
        }

        return new ShipmentListingRow(
                Long.parseLong(columns.get("Offer")),
                columns.get("Status"),
                parseExpiration(columns.get("Expiration Date")),
                columns.get("Shipment ID"),
                columns.get("Shipment Type"),
                columns.get("Rank"),
                columns.get("GBLOC"),
                columns.get("Origin"),
                columns.get("Destination"),
                columns.get("Equip Type"),
                Integer.parseInt(conveyances.group(1)),
                Integer.parseInt(conveyances.group(2)),
                LocalDate.parse(columns.get("Pickup"), DATE_FORMAT),
                LocalDate.parse(columns.get("Required Delivery"), DATE_FORMAT),
                null,
                false);
    }

    private LocalDateTime parseExpiration(String value) {
        // Every timestamp in this feed is US Eastern wall-clock time (the CSV has no separate zone
        // column) and the column itself stores LocalDateTime (see ShipmentListingRow's javadoc), so
        // this is just dropping the trailing zone abbreviation (EDT/EST) rather than converting it -
        // there's no absolute instant to compute here, only the local value as given.
        String withoutZoneAbbreviation = value.substring(0, value.lastIndexOf(' '));
        return LocalDateTime.parse(withoutZoneAbbreviation, DATE_TIME_FORMAT);
    }
}
