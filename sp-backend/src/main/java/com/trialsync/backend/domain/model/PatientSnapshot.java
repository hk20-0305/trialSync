package com.trialsync.backend.domain.model;

import java.time.LocalDate;
import java.util.List;

/**
 * The immutable patient state a screening was run against.
 *
 * <p>Port of {@code trialsync.domain.types.PatientSnapshot}. The fact list is defensively copied so
 * the engine cannot be handed a collection that changes underneath it.
 */
public record PatientSnapshot(String id, String version, LocalDate dateOfBirth, List<Fact> facts) {

    public PatientSnapshot {
        facts = facts == null ? List.of() : List.copyOf(facts);
    }

    public PatientSnapshot(String id, String version) {
        this(id, version, null, List.of());
    }
}
