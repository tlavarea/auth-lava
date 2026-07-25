package com.lava.swexpedited.vektor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import org.springframework.stereotype.Component;

/**
 * Maps one decoded {@code Drivers/Get} entry to a {@link VektorDriverRow}. Field numbers reverse-engineered from a real
 * captured response: {@code 1} driver_id (used as the roster's stable key), {@code 2} driver_number, {@code 7} email,
 * {@code 8} phone, {@code 35} full_name (already relied on by
 * {@link com.lava.swexpedited.batch.vektor.VektorDriverClient#fetchDriverNamesById}). Many other fields observed in the
 * capture aren't yet confidently understood and aren't surfaced as columns, only in {@code raw_response}.
 */
@Component
public class VektorDriverMapper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public VektorDriverRow toRow(VektorGrpcWeb.Message driver) {
        String id = driver.getString(1).orElseThrow(() -> new IllegalStateException("driver has no id (field 1)"));
        String driverNumber = driver.getString(2).orElse(null);
        String email = driver.getString(7).orElse(null);
        String phone = driver.getString(8).orElse(null);
        String fullName = driver.getString(35).orElse(null);

        return new VektorDriverRow(id, driverNumber, fullName, email, phone, null, writeAsJson(driver), null);
    }

    private String writeAsJson(VektorGrpcWeb.Message driver) {
        try {
            return objectMapper.writeValueAsString(driver.toGenericValue());
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialize a Vektor driver entry for raw_response", e);
        }
    }
}
