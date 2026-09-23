package com.trialsync.backend.dto.concept;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Response of {@code GET /api/v1/patient-fact-catalog}.
 *
 * <p>{@code version} is a frozen literal. Clients pin it to detect a catalog contract change, so it
 * must not drift with the catalog's contents.
 */
@JsonPropertyOrder({"version", "entries"})
public record PatientFactCatalogResponse(
        @JsonProperty("version") String version,
        @JsonProperty("entries") List<PatientFactCatalogEntry> entries) {

    /** The only contract version this build speaks. */
    public static final String CONTRACT_VERSION = "pd0-contract-v1";

    public PatientFactCatalogResponse(List<PatientFactCatalogEntry> entries) {
        this(CONTRACT_VERSION, entries);
    }
}
