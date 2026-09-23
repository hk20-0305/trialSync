package com.trialsync.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Per-criterion outcome after inclusion/exclusion polarity is applied. Declaration order matches the Python enum because the result counts are built by iterating it. */
public enum CriterionResult {
    PASS("pass"),
    FAIL("fail"),
    UNKNOWN("unknown");


    private final String value;

    CriterionResult(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    /** Resolves the wire value, returning {@code null} when it is not part of the contract. */
    @JsonCreator
    public static CriterionResult fromValue(String raw) {
        if (raw == null) {
            return null;
        }
        for (CriterionResult candidate : values()) {
            if (candidate.value.equals(raw)) {
                return candidate;
            }
        }
        return null;
    }
}
