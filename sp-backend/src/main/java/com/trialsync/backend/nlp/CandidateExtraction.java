package com.trialsync.backend.nlp;

import java.util.List;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * What a deterministic candidate parse produced: the candidate document and any warnings raised
 * while building it.
 *
 * <p>Port of the {@code tuple[dict[str, object], list[str]]} returned by
 * {@code extract_patient_candidates} and {@code extract_trial_candidates}.
 */
public record CandidateExtraction(ObjectNode candidates, List<String> warnings) {

    public CandidateExtraction {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
