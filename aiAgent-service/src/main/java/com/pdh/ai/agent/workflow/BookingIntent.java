package com.pdh.ai.agent.workflow;

import java.util.Locale;

public enum BookingIntent {
    FLIGHT_SEARCH,
    HOTEL_SEARCH,
    BOOKING_CREATE,
    BOOKING_CANCEL,
    BOOKING_STATUS,
    DESTINATION_GUIDE,
    WEATHER_LOOKUP,
    SMALL_TALK,
    GENERAL_ASSISTANCE,
    UNKNOWN;

    public static BookingIntent fromRaw(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (BookingIntent intent : values()) {
            if (intent.name().equals(normalized)) {
                return intent;
            }
        }
        return UNKNOWN;
    }
}
