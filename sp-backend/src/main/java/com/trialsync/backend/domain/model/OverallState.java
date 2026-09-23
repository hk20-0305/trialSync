package com.trialsync.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Overall screening state. Never decided by any model or retrieval layer. */
public enum OverallState {
    POTENTIALLY_ELIGIBLE("potentially_eligible"),
    LIKELY_INELIGIBLE("likely_ineligible"),
    NEEDS_REVIEW("needs_review");


    private final String value;

    OverallState(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    /** Resolves the wire value, returning {@code null} when it is not part of the contract. */
    @JsonCreator
    public static OverallState fromValue(String raw) {
        if (raw == null) {
            return null;
        }
        for (OverallState candidate : values()) {
            if (candidate.value.equals(raw)) {
                return candidate;
            }
        }
        return null;
    }
}
