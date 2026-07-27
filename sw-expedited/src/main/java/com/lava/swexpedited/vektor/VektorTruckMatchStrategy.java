package com.lava.swexpedited.vektor;

import com.lava.swexpedited.samsara.SamsaraVehicleRow;
import java.util.List;
import java.util.Optional;

/**
 * Best-effort join from a Vektor truck's VIN to our synced Samsara vehicle roster - there's no shared identifier
 * between the two systems, same reasoning as {@link VektorDriverMatchStrategy}. Pluggable for the same reason that
 * interface is: this is currently VIN-based, but nothing here assumes that stays the only viable join field. Returns
 * empty rather than guessing when a match is ambiguous or unavailable; {@code matched_samsara_vehicle_id} has no FK
 * constraint precisely because this is best-effort, not referential integrity.
 */
public interface VektorTruckMatchStrategy {

    Optional<String> match(String vin, List<SamsaraVehicleRow> candidates);
}
