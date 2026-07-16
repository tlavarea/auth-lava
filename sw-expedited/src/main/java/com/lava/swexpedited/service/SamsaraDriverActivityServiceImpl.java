package com.lava.swexpedited.service;

import com.lava.swexpedited.batch.SamsaraFleetClient;
import com.lava.swexpedited.samsara.DriverActivityEntry;
import com.lava.swexpedited.samsara.model.HosLogEntry;
import com.lava.swexpedited.samsara.model.HosLogLocation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Calls {@link SamsaraFleetClient#fetchDriverHosLogs(String, Instant, Instant)} live on every request - unlike
 * drivers/assignments/locations/duty-statuses, HOS logs are inherently a historical query rather than a "current state"
 * snapshot, so there's no samsara_driver_hos_log table or scheduled sync backing this.
 */
@Service
public class SamsaraDriverActivityServiceImpl implements SamsaraDriverActivityService {

    private final SamsaraFleetClient samsaraFleetClient;

    public SamsaraDriverActivityServiceImpl(SamsaraFleetClient samsaraFleetClient) {
        this.samsaraFleetClient = samsaraFleetClient;
    }

    @Override
    public List<DriverActivityEntry> findActivity(String driverId, Instant since) {
        return samsaraFleetClient.fetchDriverHosLogs(driverId, since, Instant.now()).stream()
                .map(SamsaraDriverActivityServiceImpl::toEntry)
                .sorted(Comparator.comparing(DriverActivityEntry::startTime).reversed())
                .toList();
    }

    private static DriverActivityEntry toEntry(HosLogEntry hosLogEntry) {
        HosLogLocation location = hosLogEntry.getLogRecordedLocation();
        return new DriverActivityEntry(
                hosLogEntry.getHosStatusType(),
                parseLocalDateTime(hosLogEntry.getLogStartTime()),
                parseLocalDateTime(hosLogEntry.getLogEndTime()),
                location != null ? toBigDecimal(location.getLatitude()) : null,
                location != null ? toBigDecimal(location.getLongitude()) : null,
                hosLogEntry.getRemark());
    }

    private static @Nullable BigDecimal toBigDecimal(@Nullable Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private static @Nullable LocalDateTime parseLocalDateTime(@Nullable String rfc3339) {
        return StringUtils.isNotBlank(rfc3339) ? OffsetDateTime.parse(rfc3339).toLocalDateTime() : null;
    }
}
