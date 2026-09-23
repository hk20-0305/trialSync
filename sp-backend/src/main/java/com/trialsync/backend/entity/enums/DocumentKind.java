package com.trialsync.backend.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Which kind of record a reviewed import produces, stored in the PostgreSQL {@code document_kind}
 * enum.
 *
 * <p>The constant names are the PostgreSQL labels verbatim so Hibernate's named-enum handling can
 * bind {@code name()} and read back through {@code valueOf}.
 */
public enum DocumentKind {
    patient,
    trial;

    /** The wire and storage value, identical to the constant name. */
    @JsonValue
    public String value() {
        return name();
    }

    /** Resolves the wire value, returning {@code null} when it is not part of the contract. */
    @JsonCreator
    public static DocumentKind fromValue(String raw) {
        if (raw == null) {
            return null;
        }
        for (DocumentKind candidate : values()) {
            if (candidate.name().equals(raw)) {
                return candidate;
            }
        }
        return null;
    }
}
