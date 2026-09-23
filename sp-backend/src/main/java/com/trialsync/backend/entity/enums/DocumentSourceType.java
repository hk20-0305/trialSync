package com.trialsync.backend.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How the imported document arrived, stored in the PostgreSQL {@code document_source_type} enum.
 *
 * <p>The constant names are the PostgreSQL labels verbatim so Hibernate's named-enum handling can
 * bind {@code name()} and read back through {@code valueOf}.
 */
public enum DocumentSourceType {
    text,
    pdf;

    /** The wire and storage value, identical to the constant name. */
    @JsonValue
    public String value() {
        return name();
    }

    /** Resolves the wire value, returning {@code null} when it is not part of the contract. */
    @JsonCreator
    public static DocumentSourceType fromValue(String raw) {
        if (raw == null) {
            return null;
        }
        for (DocumentSourceType candidate : values()) {
            if (candidate.name().equals(raw)) {
                return candidate;
            }
        }
        return null;
    }
}
