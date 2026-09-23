package com.trialsync.backend.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Lifecycle state of a trial version, stored in the PostgreSQL {@code version_status} enum.
 *
 * <p>The constant names are the PostgreSQL labels verbatim so Hibernate's named-enum handling can
 * bind {@code name()} and read back through {@code valueOf}.
 */
public enum VersionStatus {
    draft,
    approved;

    /** The wire and storage value, identical to the constant name. */
    @JsonValue
    public String value() {
        return name();
    }

    /** Resolves the wire value, returning {@code null} when it is not part of the contract. */
    @JsonCreator
    public static VersionStatus fromValue(String raw) {
        if (raw == null) {
            return null;
        }
        for (VersionStatus candidate : values()) {
            if (candidate.name().equals(raw)) {
                return candidate;
            }
        }
        return null;
    }
}
