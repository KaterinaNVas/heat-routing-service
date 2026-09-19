package ru.lct.heatrouting.model;

import java.util.Locale;

public enum RestrictionType {

    OKS,
    PARK,
    SOCIAL_AREA,
    PROHIBITED_SITE,
    WATER,
    ROAD,
    TRAM_TRACKS,
    GAS_PIPELINE,
    POWER_CABLE,
    HEAT_NETWORK,
    RAILWAY,
    UNKNOWN;

    public static RestrictionType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }

        String normalized = value
                .trim()
                .toUpperCase(Locale.ROOT);

        try {
            return RestrictionType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return UNKNOWN;
        }
    }
}