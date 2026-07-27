package com.lava.swexpedited.samsara;

import com.lava.swexpedited.samsara.model.Vehicle;

/**
 * A vehicle from {@code GET /fleet/vehicles} paired with the exact raw JSON of its list entry, for persisting into
 * samsara_vehicle.raw_response - see {@code SamsaraFleetClient.fetchVehicles()}.
 */
public record SamsaraVehicleWithRaw(Vehicle payload, String rawJson) {}
