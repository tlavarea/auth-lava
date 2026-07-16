package com.lava.swexpedited.batch;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

public abstract class SamsaraTasklet {

    // Samsara sends "" rather than omitting the field for some assignment timestamps (e.g. assignedAtTime on a
    // static assignment with no assignment event) - blank is treated the same as absent, not a parse failure.
    protected static @Nullable LocalDateTime parseLocalDateTime(@Nullable String rfc3339) {
        return StringUtils.isNotBlank(rfc3339) ? OffsetDateTime.parse(rfc3339).toLocalDateTime() : null;
    }
}
