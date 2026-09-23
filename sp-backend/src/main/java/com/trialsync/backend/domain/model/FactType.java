package com.trialsync.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Category of a recorded patient fact. */
public enum FactType {
    DEMOGRAPHIC("demographic"),
    CONDITION("condition"),
    MEDICATION("medication"),
    OBSERVATION("observation");


    private final String value;

    FactType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    /** Resolves the wire value, returning {@code null} when it is not part of the contract. */
    @JsonCreator
    public static FactType fromValue(String raw) {
        if (raw == null) {
            return null;
        }
        for (FactType candidate : values()) {
            if (candidate.value.equals(raw)) {
                return candidate;
            }
        }
        return null;
    }
}
