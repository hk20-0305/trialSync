package com.trialsync.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.domain.model.Assertion;
import com.trialsync.backend.domain.model.FactType;
import com.trialsync.backend.dto.concept.ClinicalConceptCreateRequest;
import com.trialsync.backend.dto.concept.ClinicalConceptResponse;
import com.trialsync.backend.dto.concept.ClinicalConceptUpdateRequest;
import com.trialsync.backend.dto.concept.TerminologySuggestionResponse;
import com.trialsync.backend.entity.ClinicalConcept;
import com.trialsync.backend.entity.User;
import com.trialsync.backend.repository.ClinicalConceptRepository;
import com.trialsync.backend.security.SecurityContext;
import com.trialsync.backend.terminology.TerminologySuggestionResult;
import com.trialsync.backend.terminology.TerminologySuggestionService;

/**
 * Catalog administration: the orchestration behind {@code trialsync.api.clinical_concepts}.
 *
 * <p>Every route here is restricted to catalog administrators, because a concept is not user data -
 * it is the closed vocabulary the screening engine reasons over. Letting an ordinary account add to
 * it would let a caller invent a fact type and then screen against it.
 *
 * <p>A concept's identity is derived, never submitted. The key and the stored concept string both
 * come from the label, and the group, input kind, admissible assertions and date requirement are
 * decided by the fact type. That is what keeps a numeric observation from being authored with
 * status answers, and it is why the edit route exposes only the three presentation fields: changing
 * the derived ones would silently re-interpret every fact already recorded against the concept.
 *
 * <p>Retiring is a soft flag. Facts recorded against a retired concept keep resolving for reading
 * and screening; the concept simply stops being selectable, so history is never invalidated by an
 * administrative decision.
 */
@Service
public class ClinicalConceptService {

    /** Bounds from {@code ClinicalConceptCreate} and {@code ClinicalConceptUpdate}. */
    private static final int MAXIMUM_DISPLAY_LABEL = 120;

    private static final int MAXIMUM_FIXED_UNIT = 40;

    private static final int MAXIMUM_HELP_TEXT = 300;

    private static final int MAXIMUM_TERMINOLOGY_CODE = 80;

    /** {@code Query(min_length=2, max_length=100)} on the suggestions lookup. */
    private static final int MINIMUM_SUGGESTION_QUERY = 2;

    private static final int MAXIMUM_SUGGESTION_QUERY = 100;

    /** {@code CatalogFactType}: the catalog is narrower than {@link FactType}. */
    private static final List<String> CATALOG_FACT_TYPES =
            List.of(FactType.CONDITION.value(), FactType.MEDICATION.value(), FactType.OBSERVATION.value());

    private static final String RXNORM = "rxnorm";

    private static final String LOINC = "loinc";

    /** New concepts are appended a fixed step past the last curated position in their group. */
    private static final int DISPLAY_ORDER_STEP = 10;

    private final ClinicalConceptRepository conceptRepository;
    private final PatientFactCatalogService catalog;
    private final TerminologySuggestionService terminologySuggestions;
    private final ObjectMapper objectMapper;

    public ClinicalConceptService(
            ClinicalConceptRepository conceptRepository,
            PatientFactCatalogService catalog,
            TerminologySuggestionService terminologySuggestions,
            ObjectMapper objectMapper) {
        this.conceptRepository = conceptRepository;
        this.catalog = catalog;
        this.terminologySuggestions = terminologySuggestions;
        this.objectMapper = objectMapper;
    }

    /**
     * The whole catalog, retired entries included.
     *
     * <p>Ordered active-first so a retired concept falls to the bottom of the administration table
     * rather than sitting among the selectable ones.
     */
    @Transactional(readOnly = true)
    public List<ClinicalConceptResponse> list() {
        requireCatalogAdmin();
        List<ClinicalConceptResponse> responses = new ArrayList<>();
        for (ClinicalConcept record : conceptRepository.findAllForAdministration()) {
            responses.add(toResponse(record));
        }
        return responses;
    }

    /**
     * Candidate codings for a concept an administrator is about to author.
     *
     * <p>Advisory only, and deliberately outside any transaction: the lookup leaves the database
     * untouched and an unreachable terminology server answers 200 with an explanation rather than
     * failing the request, so the concept can still be authored by hand.
     */
    public TerminologySuggestionResponse suggestions(String factType, String query) {
        FactType resolved = requireSuggestionFactType(factType);
        String validated = requireSuggestionQuery(query);
        requireCatalogAdmin();
        // The length bounds are judged on the raw parameter and the lookup runs on the trimmed
        // value, which is the order `Query(min_length=2)` plus `query.strip()` produces.
        String trimmed = validated.strip();
        TerminologySuggestionResult result = terminologySuggestions.suggest(trimmed, resolved);
        return new TerminologySuggestionResponse(
                trimmed, result.suggestions(), result.unavailableSources());
    }

    /**
     * Adds a concept to the catalog.
     *
     * <p>The label is the only identity the caller supplies: the key and the stored concept string
     * are both derived from it, so two administrators authoring "C-reactive protein" collide on
     * {@code c_reactive_protein} and the second is told the concept already exists rather than
     * creating a second entry the engine would treat as unrelated.
     */
    @Transactional
    public ClinicalConceptResponse create(ClinicalConceptCreateRequest payload) {
        ValidatedConcept validated = validateCreate(payload);
        requireCatalogAdmin();

        String key = PatientDataFormats.catalogKey(validated.displayLabel());
        if (key.isEmpty()) {
            throw ApplicationError.unprocessable(
                    "CATALOG_KEY_INVALID",
                    "Use a label containing letters or numbers.",
                    "display_label");
        }
        ConceptMetadata metadata = metadata(validated);
        Integer highest = conceptRepository.findMaxDisplayOrder(metadata.conceptGroup());
        int displayOrder = (highest == null ? 0 : highest) + DISPLAY_ORDER_STEP;

        ClinicalConcept concept =
                new ClinicalConcept(key, validated.factType(), key, validated.displayLabel());
        concept.setConceptGroup(metadata.conceptGroup());
        concept.setInputKind(metadata.inputKind());
        concept.setAllowedAssertionsJson(serializeAssertions(metadata.allowedAssertions()));
        concept.setEffectiveDateRequired(metadata.effectiveDateRequired());
        concept.setHelpText(metadata.helpText());
        concept.setFixedUnit(validated.fixedUnit());
        concept.setScreeningSupported(validated.screeningSupported());
        concept.setTerminologySystem(validated.terminologySystem());
        concept.setTerminologyCode(validated.terminologyCode());
        concept.setDisplayOrder(displayOrder);
        concept.setActive(true);

        ClinicalConcept saved;
        try {
            saved = conceptRepository.saveAndFlush(concept);
        } catch (DataIntegrityViolationException exception) {
            // The unique key is derived from the label, so the collision a caller sees is a label
            // that is already taken - which is the field the error names.
            throw new ApplicationError(
                    "CATALOG_CONCEPT_EXISTS",
                    "A clinical concept with this label already exists.",
                    409,
                    "display_label");
        }
        return toResponse(saved);
    }

    /**
     * Edits a concept's presentation.
     *
     * <p>Only the label, the screening flag and the help text are writable, and only when the
     * caller actually sent them. The key, group, input kind and assertion whitelist are left alone
     * because facts already reference them; re-deriving the key from a new label would orphan
     * every one of those rows.
     */
    @Transactional
    public ClinicalConceptResponse update(UUID conceptId, ClinicalConceptUpdateRequest payload) {
        validateUpdate(payload);
        requireCatalogAdmin();

        ClinicalConcept concept = managedConcept(conceptId);
        if (payload.isDisplayLabelSupplied()) {
            concept.setDisplayLabel(PatientDataFormats.collapseToNull(payload.getDisplayLabel()));
        }
        if (payload.isScreeningSupportedSupplied()) {
            concept.setScreeningSupported(Boolean.TRUE.equals(payload.getScreeningSupported()));
        }
        if (payload.isHelpTextSupplied()) {
            concept.setHelpText(PatientDataFormats.collapseToNull(payload.getHelpText()));
        }
        return toResponse(conceptRepository.saveAndFlush(concept));
    }

    /** Withdraws a concept from selection without deleting it or the facts that reference it. */
    @Transactional
    public ClinicalConceptResponse retire(UUID conceptId) {
        requireCatalogAdmin();
        ClinicalConcept concept = managedConcept(conceptId);
        concept.setActive(false);
        return toResponse(conceptRepository.saveAndFlush(concept));
    }

    /** Returns a retired concept to selection. */
    @Transactional
    public ClinicalConceptResponse restore(UUID conceptId) {
        requireCatalogAdmin();
        ClinicalConcept concept = managedConcept(conceptId);
        concept.setActive(true);
        return toResponse(conceptRepository.saveAndFlush(concept));
    }

    // ------------------------------------------------------------------ guards

    /**
     * Port of {@code require_catalog_admin}.
     *
     * <p>Deliberately a 403 rather than the 404 the owner-scoped resources answer: a concept is
     * shared rather than tenanted, so there is no identifier to conceal, and an administrator
     * needs to be told the difference between "no such concept" and "not your call".
     */
    private static void requireCatalogAdmin() {
        User user = SecurityContext.require();
        if (!user.isCatalogAdmin()) {
            throw new ApplicationError(
                    "CATALOG_ADMIN_REQUIRED",
                    "Catalog management is available only to catalog administrators.",
                    403);
        }
    }

    /** Port of {@code managed_concept}. */
    private ClinicalConcept managedConcept(UUID conceptId) {
        return conceptRepository
                .findById(conceptId)
                .orElseThrow(
                        () ->
                                ApplicationError.notFound(
                                        "CATALOG_CONCEPT_NOT_FOUND",
                                        "The clinical concept was not found."));
    }

    // -------------------------------------------------------------- validation

    /** The normalized create payload, after the Pydantic validators have been reproduced. */
    private record ValidatedConcept(
            String displayLabel,
            FactType factType,
            String fixedUnit,
            boolean screeningSupported,
            String helpText,
            String terminologySystem,
            String terminologyCode) {}

    /** The fields {@code concept_metadata} derives from the fact type. */
    private record ConceptMetadata(
            String conceptGroup,
            String inputKind,
            List<String> allowedAssertions,
            boolean effectiveDateRequired,
            String helpText) {}

    /**
     * The field validators and the model validator of {@code ClinicalConceptCreate}.
     *
     * <p>The text fields run through {@code " ".join(value.split())} and an empty result becomes
     * null, so a label is normalized before its bounds are judged and can never be stored blank.
     * The cross-field rules are checked last and in their declared order, because the first failure
     * is the one Pydantic reports for a model validator.
     */
    private ValidatedConcept validateCreate(ClinicalConceptCreateRequest payload) {
        String displayLabel = PatientDataFormats.collapseToNull(payload.displayLabel());
        if (displayLabel == null) {
            // The normalizer turns a blank label into null before the field rules run, so a label
            // of nothing but spaces fails as a bad string rather than as an absent one - the same
            // split `" ".join(value.split()) or None` produces against a required `str` field.
            throw payload.displayLabel() == null
                    ? PatientValidationErrors.requestInvalid(
                            "display_label", PatientValidationErrors.fieldRequired(), "missing")
                    : PatientValidationErrors.requestInvalid(
                            "display_label", "Input should be a valid string", "string_type");
        }
        if (displayLabel.length() > MAXIMUM_DISPLAY_LABEL) {
            throw PatientValidationErrors.requestInvalid(
                    "display_label",
                    PatientValidationErrors.tooLong(MAXIMUM_DISPLAY_LABEL),
                    "string_too_long");
        }

        if (payload.factType() == null) {
            throw PatientValidationErrors.requestInvalid(
                    "fact_type", PatientValidationErrors.fieldRequired(), "missing");
        }
        if (!CATALOG_FACT_TYPES.contains(payload.factType())) {
            throw PatientValidationErrors.requestInvalid(
                    "fact_type",
                    "Input should be 'condition', 'medication' or 'observation'",
                    "literal_error");
        }
        FactType factType = FactType.fromValue(payload.factType());

        String fixedUnit = PatientDataFormats.collapseToNull(payload.fixedUnit());
        if (fixedUnit != null && fixedUnit.length() > MAXIMUM_FIXED_UNIT) {
            throw PatientValidationErrors.requestInvalid(
                    "fixed_unit",
                    PatientValidationErrors.tooLong(MAXIMUM_FIXED_UNIT),
                    "string_too_long");
        }

        String helpText = PatientDataFormats.collapseToNull(payload.helpText());
        if (helpText != null && helpText.length() > MAXIMUM_HELP_TEXT) {
            throw PatientValidationErrors.requestInvalid(
                    "help_text",
                    PatientValidationErrors.tooLong(MAXIMUM_HELP_TEXT),
                    "string_too_long");
        }

        String terminologySystem = payload.terminologySystem();
        if (terminologySystem != null
                && !RXNORM.equals(terminologySystem)
                && !LOINC.equals(terminologySystem)) {
            throw PatientValidationErrors.requestInvalid(
                    "terminology_system", "Input should be 'rxnorm' or 'loinc'", "literal_error");
        }

        String terminologyCode = payload.terminologyCode();
        if (terminologyCode != null && terminologyCode.isEmpty()) {
            throw PatientValidationErrors.requestInvalid(
                    "terminology_code", PatientValidationErrors.tooShort(1), "string_too_short");
        }
        if (terminologyCode != null && terminologyCode.length() > MAXIMUM_TERMINOLOGY_CODE) {
            throw PatientValidationErrors.requestInvalid(
                    "terminology_code",
                    PatientValidationErrors.tooLong(MAXIMUM_TERMINOLOGY_CODE),
                    "string_too_long");
        }

        validateInputShape(factType, fixedUnit, terminologySystem, terminologyCode);
        return new ValidatedConcept(
                displayLabel,
                factType,
                fixedUnit,
                payload.screeningSupported() == null || payload.screeningSupported(),
                helpText,
                terminologySystem,
                terminologyCode);
    }

    /**
     * Port of {@code validate_input_shape}.
     *
     * <p>A unit is what makes a measurement comparable, so an observation must pin one and a
     * condition must not carry one at all - a "present" answer in mg/dL would be meaningless to the
     * engine. The terminology pair is all-or-nothing and each system is tied to the fact type it
     * actually codes, so a LOINC code cannot be attached to a medication.
     */
    private static void validateInputShape(
            FactType factType, String fixedUnit, String terminologySystem, String terminologyCode) {
        if (factType == FactType.OBSERVATION && fixedUnit == null) {
            throw modelValidationError("Observations require a fixed unit.");
        }
        if (factType != FactType.OBSERVATION && fixedUnit != null) {
            throw modelValidationError("Conditions and medications do not use a unit.");
        }
        if ((terminologySystem == null) != (terminologyCode == null)) {
            throw modelValidationError("A terminology system and code must be supplied together.");
        }
        if (RXNORM.equals(terminologySystem) && factType != FactType.MEDICATION) {
            throw modelValidationError("RxNorm suggestions may be used only for medications.");
        }
        if (LOINC.equals(terminologySystem) && factType != FactType.OBSERVATION) {
            throw modelValidationError("LOINC suggestions may be used only for observations.");
        }
    }

    /**
     * The field validators of {@code ClinicalConceptUpdate}.
     *
     * <p>There is no cross-field rule here, and no staleness guard either: the Python route has
     * none, so a catalog edit is last-write-wins. That is defensible for a small set of
     * administrators editing presentation text, and it is the behaviour clients already see.
     */
    private static void validateUpdate(ClinicalConceptUpdateRequest payload) {
        String displayLabel = PatientDataFormats.collapseToNull(payload.getDisplayLabel());
        if (displayLabel != null && displayLabel.length() > MAXIMUM_DISPLAY_LABEL) {
            throw PatientValidationErrors.requestInvalid(
                    "display_label",
                    PatientValidationErrors.tooLong(MAXIMUM_DISPLAY_LABEL),
                    "string_too_long");
        }
        String helpText = PatientDataFormats.collapseToNull(payload.getHelpText());
        if (helpText != null && helpText.length() > MAXIMUM_HELP_TEXT) {
            throw PatientValidationErrors.requestInvalid(
                    "help_text",
                    PatientValidationErrors.tooLong(MAXIMUM_HELP_TEXT),
                    "string_too_long");
        }
    }

    /** {@code fact_type: Annotated[FactType, Query()]} - the full enum, not the catalog subset. */
    private static FactType requireSuggestionFactType(String factType) {
        if (factType == null) {
            throw PatientValidationErrors.requestInvalidQuery(
                    "fact_type", PatientValidationErrors.fieldRequired(), "missing");
        }
        FactType resolved = FactType.fromValue(factType);
        if (resolved == null) {
            throw PatientValidationErrors.requestInvalidQuery(
                    "fact_type",
                    "Input should be 'condition', 'medication', 'observation' or 'demographic'",
                    "enum");
        }
        return resolved;
    }

    /** {@code query: Annotated[str, Query(min_length=2, max_length=100)]}. */
    private static String requireSuggestionQuery(String query) {
        if (query == null) {
            throw PatientValidationErrors.requestInvalidQuery(
                    "query", PatientValidationErrors.fieldRequired(), "missing");
        }
        if (query.length() < MINIMUM_SUGGESTION_QUERY) {
            throw PatientValidationErrors.requestInvalidQuery(
                    "query",
                    PatientValidationErrors.tooShort(MINIMUM_SUGGESTION_QUERY),
                    "string_too_short");
        }
        if (query.length() > MAXIMUM_SUGGESTION_QUERY) {
            throw PatientValidationErrors.requestInvalidQuery(
                    "query",
                    PatientValidationErrors.tooLong(MAXIMUM_SUGGESTION_QUERY),
                    "string_too_long");
        }
        return query;
    }

    /**
     * A {@code ValueError} raised from a Pydantic model validator, which reports against the body
     * as a whole rather than against one field and prefixes the message with "Value error, ".
     */
    private static ApplicationError modelValidationError(String message) {
        return PatientValidationErrors.requestInvalid(
                null, "Value error, " + message, "value_error");
    }

    // ----------------------------------------------------------------- mapping

    /**
     * Port of {@code concept_metadata}.
     *
     * <p>An observation is a measurement: it is numeric, it admits only "present" or "unknown" -
     * there is no such thing as an absent blood test result - and it requires the date it was
     * taken, without which a threshold comparison cannot be placed in time. A condition or
     * medication is a standing answer, so it takes the three-way status and needs no date.
     */
    private static ConceptMetadata metadata(ValidatedConcept payload) {
        if (payload.factType() == FactType.OBSERVATION) {
            return new ConceptMetadata(
                    "observations",
                    "numeric",
                    List.of(Assertion.PRESENT.value(), Assertion.UNKNOWN.value()),
                    true,
                    payload.helpText() == null
                            ? "Record the measured " + payload.displayLabel() + " result."
                            : payload.helpText());
        }
        String group = payload.factType() == FactType.CONDITION ? "conditions" : "medications";
        return new ConceptMetadata(
                group,
                "status",
                List.of(Assertion.PRESENT.value(), Assertion.ABSENT.value(), Assertion.UNKNOWN.value()),
                false,
                payload.helpText() == null
                        ? "Record whether "
                                + payload.displayLabel()
                                + " is present, absent, or unknown."
                        : payload.helpText());
    }

    /** Port of {@code ClinicalConceptRead}. */
    private ClinicalConceptResponse toResponse(ClinicalConcept record) {
        return new ClinicalConceptResponse(
                record.getId(),
                record.getKey(),
                record.getFactType(),
                record.getConcept(),
                record.getDisplayLabel(),
                record.getConceptGroup(),
                record.getInputKind(),
                catalog.allowedAssertions(record),
                record.getFixedUnit(),
                record.isEffectiveDateRequired(),
                record.isScreeningSupported(),
                record.getHelpText(),
                record.getTerminologySystem(),
                record.getTerminologyCode(),
                record.getDisplayOrder(),
                record.isActive(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }

    /** The assertion whitelist is stored as a JSON array of wire values. */
    private String serializeAssertions(List<String> assertions) {
        try {
            return objectMapper.writeValueAsString(assertions);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "A clinical concept assertion list could not be serialized", exception);
        }
    }
}
