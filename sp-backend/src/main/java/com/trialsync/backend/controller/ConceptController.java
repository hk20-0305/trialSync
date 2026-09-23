package com.trialsync.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.trialsync.backend.dto.concept.ClinicalConceptCreateRequest;
import com.trialsync.backend.dto.concept.ClinicalConceptResponse;
import com.trialsync.backend.dto.concept.ClinicalConceptUpdateRequest;
import com.trialsync.backend.dto.concept.TerminologySuggestionResponse;
import com.trialsync.backend.service.ClinicalConceptService;

import jakarta.validation.Valid;

/**
 * Port of {@code trialsync.api.clinical_concepts}: administration of the controlled catalog.
 *
 * <p>Every route is restricted to catalog administrators, and the check lives in
 * {@link ClinicalConceptService} rather than here - it is a rule about who may change the shared
 * vocabulary, not a detail of HTTP binding.
 *
 * <p>{@code /suggestions} is declared as a literal segment, which Spring matches ahead of the
 * {@code {conceptId}} template, so a lookup is never mistaken for a concept edit.
 *
 * <p>The two query parameters on that route are bound as strings and validated in the service. The
 * fact type is spelled in lower case on the wire, which Spring's default enum binding does not
 * accept, and binding it loosely is what lets an unrecognised value answer the same validation
 * envelope FastAPI produced rather than a framework type-mismatch.
 */
@RestController
@RequestMapping("/api/v1/clinical-concepts")
public class ConceptController {

    private final ClinicalConceptService clinicalConceptService;

    public ConceptController(ClinicalConceptService clinicalConceptService) {
        this.clinicalConceptService = clinicalConceptService;
    }

    /** The whole catalog, retired concepts included, active ones first. */
    @GetMapping
    public List<ClinicalConceptResponse> listClinicalConcepts() {
        return clinicalConceptService.list();
    }

    /** Candidate RxNorm or LOINC codings for a concept about to be authored. */
    @GetMapping("/suggestions")
    public TerminologySuggestionResponse terminologySuggestions(
            @RequestParam(name = "fact_type") String factType,
            @RequestParam(name = "query") String query) {
        return clinicalConceptService.suggestions(factType, query);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClinicalConceptResponse createClinicalConcept(
            @Valid @RequestBody ClinicalConceptCreateRequest payload) {
        return clinicalConceptService.create(payload);
    }

    @PatchMapping("/{conceptId}")
    public ClinicalConceptResponse updateClinicalConcept(
            @PathVariable UUID conceptId, @Valid @RequestBody ClinicalConceptUpdateRequest payload) {
        return clinicalConceptService.update(conceptId, payload);
    }

    /** Withdraws a concept from selection. Takes no body. */
    @PostMapping("/{conceptId}/retire")
    public ClinicalConceptResponse retireClinicalConcept(@PathVariable UUID conceptId) {
        return clinicalConceptService.retire(conceptId);
    }

    /** Returns a retired concept to selection. Takes no body. */
    @PostMapping("/{conceptId}/restore")
    public ClinicalConceptResponse restoreClinicalConcept(@PathVariable UUID conceptId) {
        return clinicalConceptService.restore(conceptId);
    }
}
