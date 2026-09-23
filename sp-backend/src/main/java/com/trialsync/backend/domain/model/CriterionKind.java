package com.trialsync.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Whether satisfying a criterion admits or excludes the patient. */
public enum CriterionKind {
    INCLUSION("inclusion"),
    EXCLUSION("exclusion");


    private final String value;

    CriterionKind(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    /** Resolves the wire value, returning {@code null} when it is not part of the contract. */
    @JsonCreator
    public static CriterionKind fromValue(String raw) {
        if (raw == null) {
            return null;
        }
        for (CriterionKind candidate : values()) {
            if (candidate.value.equals(raw)) {
                return candidate;
            }
        }
        return null;
    }
}
