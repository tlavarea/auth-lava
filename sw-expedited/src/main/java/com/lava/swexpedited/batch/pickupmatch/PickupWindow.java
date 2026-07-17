package com.lava.swexpedited.batch.pickupmatch;

import java.time.LocalDateTime;

/** A shipment's GFM-quoted pickup window, parsed by {@link PickupWindowMapper}. */
record PickupWindow(LocalDateTime earliest, LocalDateTime latest) {}
