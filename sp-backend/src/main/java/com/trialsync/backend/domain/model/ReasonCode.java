package com.trialsync.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Audit reason attached to every evaluation. These strings are part of the stored record and the API contract. */
public enum ReasonCode {
    EVALUATED_TRUE("EVALUATED_TRUE"),
    EVALUATED_FALSE("EVALUATED_FALSE"),
    MISSING_FACT("MISSING_FACT"),
    STALE_EVIDENCE("STALE_EVIDENCE"),
    CONFLICTING_EVIDENCE("CONFLICTING_EVIDENCE"),
    INCOMPATIBLE_UNIT("INCOMPATIBLE_UNIT"),
    UNSUPPORTED_RULE("UNSUPPORTED_RULE"),
    INVALID_RULE("INVALID_RULE");


    private final String value;

    ReasonCode(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    /** Resolves the wire value, returning {@code null} when it is not part of the contract. */
    @JsonCreator
    public static ReasonCode fromValue(String raw) {
        if (raw == null) {
            return null;
        }
        for (ReasonCode candidate : values()) {
            if (candidate.value.equals(raw)) {
                return candidate;
            }
        }
        return null;
    }
}
