package com.lava.swexpedited.samsara;

/** Reverse-geocoded street address for a {@link SamsaraSafetyEventLocation}. */
public record SamsaraSafetyEventAddress(String street, String city, String state, String postalCode) {}
