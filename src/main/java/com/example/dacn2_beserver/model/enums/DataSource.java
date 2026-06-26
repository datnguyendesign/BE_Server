package com.example.dacn2_beserver.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum DataSource {
    MANUAL,
    SMARTWATCH,
    PHONE_SENSOR,
    AI_INFERRED;

    @JsonCreator
    public static DataSource fromJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim().toUpperCase(Locale.ROOT);
        if (v.equals("TIMER")) {
            return MANUAL; // timer logs are user-originated
        }
        for (DataSource ds : values()) {
            if (ds.name().equals(v)) {
                return ds;
            }
        }
        return null; // lenient: unknown source tag ignored, request still succeeds
    }
}
