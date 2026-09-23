package com.trialsync.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trialsync.backend.dto.concept.PatientFactCatalogResponse;
import com.trialsync.backend.service.PatientFactCatalogService;

/**
 * Port of {@code trialsync.api.patient_fact_catalog}: the controlled vocabulary the patient and
 * protocol forms select from.
 *
 * <p>The catalog is shared rather than owned, so the route reads the same rows for everybody. It
 * still requires a signed-in caller - Python resolves the user and immediately discards it, and the
 * equivalent here is that the path is not public, so the security filter authenticates it before
 * the handler runs. Nothing about the response varies by who asked.
 *
 * <p>Only active concepts are returned. A retired one disappears from the form and can no longer be
 * chosen, while facts already recorded against it keep their rows and keep screening.
 */
@RestController
@RequestMapping("/api/v1/patient-fact-catalog")
public class FactCatalogController {

    private final PatientFactCatalogService patientFactCatalogService;

    public FactCatalogController(PatientFactCatalogService patientFactCatalogService) {
        this.patientFactCatalogService = patientFactCatalogService;
    }

    @GetMapping
    public PatientFactCatalogResponse getPatientFactCatalog() {
        return patientFactCatalogService.catalog();
    }
}
