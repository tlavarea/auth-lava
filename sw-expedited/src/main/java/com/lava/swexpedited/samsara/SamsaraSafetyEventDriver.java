package com.lava.swexpedited.samsara;

/**
 * The driver associated with a {@link SamsaraSafetyEvent}, present because {@code includeDriver=true} is always sent.
 */
public record SamsaraSafetyEventDriver(String id, String name) {}
