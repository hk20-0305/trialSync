package com.trialsync.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** When a fact applies relative to the screening date. */
public enum Temporality {
    CURRENT("current"),
    HISTORICAL("historical"),
    RESOLVED("resolved"),
    UNKNOWN("unknown");


    private final String value;

    Temporality(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    /** Resolves the wire value, returning {@code null} when it is not part of the contract. */
    @JsonCreator
    public static Temporality fromValue(String raw) {
        if (raw == null) {
            return null;
        }
        for (Temporality candidate : values()) {
            if (candidate.value.equals(raw)) {
                return candidate;
            }
        }
        return null;
    }
}
