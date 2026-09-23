package com.trialsync.backend.dto.imports;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Port of {@code trialsync.imports.schemas.PatientImportCandidates}. */
public record PatientImportCandidates(
        @JsonProperty("profile") PatientProfileCandidate profile,
        @JsonProperty("facts") List<PatientFactCandidate> facts) {

    /** Returns a copy carrying a rebuilt fact list, used after catalog annotation. */
    public PatientImportCandidates withFacts(List<PatientFactCandidate> replacement) {
        return new PatientImportCandidates(profile, replacement);
    }
}
