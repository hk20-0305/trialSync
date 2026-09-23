package com.trialsync.backend.nlp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The outcome of one extraction attempt: {@code trialsync.nlp.extraction.ExtractionRun}.
 *
 * <p>{@code candidates} is a proposal, never a decision. Everything in it is shown to a human
 * reviewer who selects, edits or discards it before any patient or trial record is written, which
 * is what keeps a language model out of the eligibility path entirely.
 *
 * <p>{@code metadata} is provenance for that reviewer - which provider ran, which model, which
 * prompt version, how long it took, and how its output validated.
 */
public record ExtractionRun(
        ObjectNode candidates, List<String> warnings, Map<String, Object> metadata) {

    public ExtractionRun {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        metadata = metadata == null ? Map.of() : new LinkedHashMap<>(metadata);
    }
}
