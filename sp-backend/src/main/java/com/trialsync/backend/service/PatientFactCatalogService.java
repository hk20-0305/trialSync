package com.trialsync.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trialsync.backend.domain.model.FactType;
import com.trialsync.backend.dto.concept.PatientFactCatalogEntry;
import com.trialsync.backend.dto.concept.PatientFactCatalogResponse;
import com.trialsync.backend.entity.ClinicalConcept;
import com.trialsync.backend.repository.ClinicalConceptRepository;

/**
 * The controlled catalog, as the patient data-entry path sees it.
 *
 * <p>Port of {@code trialsync.patient_data.catalog}. Every read filters on {@code active}, so a
 * retired concept disappears from the form and can no longer be chosen, while facts already
 * recorded against it keep their row. The same entry object serves two purposes, exactly as it does
 * in Python: it is the JSON the form renders, and it is the rule set a submitted fact is validated
 * against.
 */
@Service
public class PatientFactCatalogService {

    private static final TypeReference<List<String>> ASSERTION_LIST = new TypeReference<>() {};

    private final ClinicalConceptRepository conceptRepository;
    private final ObjectMapper objectMapper;

    public PatientFactCatalogService(
            ClinicalConceptRepository conceptRepository, ObjectMapper objectMapper) {
        this.conceptRepository = conceptRepository;
        this.objectMapper = objectMapper;
    }

    /** The catalog served to the patient and protocol forms, grouped and in curated order. */
    @Transactional(readOnly = true)
    public PatientFactCatalogResponse catalog() {
        List<PatientFactCatalogEntry> entries = new ArrayList<>();
        for (ClinicalConcept record :
                conceptRepository.findByActiveTrueOrderByConceptGroupAscDisplayOrderAsc()) {
            entries.add(toEntry(record));
        }
        return new PatientFactCatalogResponse(entries);
    }

    /** Resolves the entry a fact submission names, or empty when it is not selectable. */
    @Transactional(readOnly = true)
    public Optional<PatientFactCatalogEntry> activeEntryByKey(String key) {
        return conceptRepository.findByKeyAndActiveTrue(key).map(this::toEntry);
    }

    /**
     * Resolves the entry behind a stored fact.
     *
     * <p>Empty means the fact predates the catalog or its concept has been retired. The endpoints
     * treat that as a reason to refuse the edit rather than to guess a rule set.
     */
    @Transactional(readOnly = true)
    public Optional<PatientFactCatalogEntry> activeEntryByFact(FactType factType, String concept) {
        return conceptRepository
                .findByFactTypeAndConceptAndActiveTrue(factType, concept)
                .map(this::toEntry);
    }

    /**
     * Port of {@code catalog_entry_from_record}.
     *
     * <p>The concept's terminology coding is intentionally dropped: it exists for catalog
     * administration and is not part of the entry contract the data-entry forms read.
     * {@code allowed_units} is always empty, because every numeric concept today pins one
     * {@code fixed_unit}.
     */
    public PatientFactCatalogEntry toEntry(ClinicalConcept record) {
        return new PatientFactCatalogEntry(
                record.getKey(),
                record.getFactType(),
                record.getConcept(),
                record.getDisplayLabel(),
                record.getConceptGroup(),
                record.getInputKind(),
                allowedAssertions(record),
                record.getFixedUnit(),
                List.of(),
                record.isEffectiveDateRequired(),
                record.isScreeningSupported(),
                record.getHelpText(),
                record.getDisplayOrder());
    }

    /** Reads the concept's assertion whitelist, stored as a JSON array of wire values. */
    public List<String> allowedAssertions(ClinicalConcept record) {
        try {
            return objectMapper.readValue(record.getAllowedAssertionsJson(), ASSERTION_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Clinical concept " + record.getKey() + " has an unreadable assertion list",
                    exception);
        }
    }
}
