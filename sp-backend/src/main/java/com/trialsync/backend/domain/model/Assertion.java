package com.trialsync.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Whether a fact is asserted to hold, not hold, or be undetermined. */
public enum Assertion {
    PRESENT("present"),
    ABSENT("absent"),
    UNKNOWN("unknown");


    private final String value;

    Assertion(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    /** Resolves the wire value, returning {@code null} when it is not part of the contract. */
    @JsonCreator
    public static Assertion fromValue(String raw) {
        if (raw == null) {
            return null;
        }
        for (Assertion candidate : values()) {
            if (candidate.value.equals(raw)) {
                return candidate;
            }
        }
        return null;
    }
}
