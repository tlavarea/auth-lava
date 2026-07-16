package com.lava.swexpedited.samsara;

import com.lava.swexpedited.samsara.model.Driver;

/**
 * A driver from {@code GET /fleet/drivers} paired with the exact raw JSON of its list entry, for persisting into
 * samsara_driver.raw_response - see {@code SamsaraFleetClient.fetchDrivers()}.
 */
public record SamsaraDriverWithRaw(Driver payload, String rawJson) {}
