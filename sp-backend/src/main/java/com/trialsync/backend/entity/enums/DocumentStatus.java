package com.trialsync.backend.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Review state of an imported document, stored in the PostgreSQL {@code document_status} enum.
 *
 * <p>The constant names are the PostgreSQL labels verbatim so Hibernate's named-enum handling can
 * bind {@code name()} and read back through {@code valueOf}.
 */
public enum DocumentStatus {
    needs_review,
    approved,
    rejected;

    /** The wire and storage value, identical to the constant name. */
    @JsonValue
    public String value() {
        return name();
    }

    /** Resolves the wire value, returning {@code null} when it is not part of the contract. */
    @JsonCreator
    public static DocumentStatus fromValue(String raw) {
        if (raw == null) {
            return null;
        }
        for (DocumentStatus candidate : values()) {
            if (candidate.name().equals(raw)) {
                return candidate;
            }
        }
        return null;
    }
}
