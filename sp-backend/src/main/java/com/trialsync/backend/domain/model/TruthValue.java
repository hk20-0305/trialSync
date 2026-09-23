package com.trialsync.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Three-valued truth used throughout rule evaluation. */
public enum TruthValue {
    TRUE("true"),
    FALSE("false"),
    UNKNOWN("unknown");


    private final String value;

    TruthValue(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    /** Resolves the wire value, returning {@code null} when it is not part of the contract. */
    @JsonCreator
    public static TruthValue fromValue(String raw) {
        if (raw == null) {
            return null;
        }
        for (TruthValue candidate : values()) {
            if (candidate.value.equals(raw)) {
                return candidate;
            }
        }
        return null;
    }
}
