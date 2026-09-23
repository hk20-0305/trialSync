package com.trialsync.backend.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.dto.concept.PatientFactCatalogEntry;
import com.trialsync.backend.dto.patient.FactResponse;
import com.trialsync.backend.dto.patient.FactValue;
import com.trialsync.backend.dto.patient.PatientFactCreateRequest;
import com.trialsync.backend.dto.patient.PatientFactUpdateRequest;
import com.trialsync.backend.dto.patient.PatientFactVoidRequest;
import com.trialsync.backend.entity.Patient;
import com.trialsync.backend.entity.PatientFact;
import com.trialsync.backend.entity.TimestampedEntity;
import com.trialsync.backend.repository.PatientFactRepository;
import com.trialsync.backend.security.SecurityContext;

import jakarta.persistence.EntityManager;

/**
 * Recording, editing, removing and restoring one patient's clinical details.
 *
 * <p>Facts are only ever written through the controlled catalog: the request names a catalog key
 * and supplies a value, and the concept behind that key decides the fact type, the concept string,
 * the unit and which assertions are admissible. Nothing here lets a caller invent a concept, which
 * is what keeps the screening engine's inputs closed.
 *
 * <p>Removal is a soft void with a mandatory reason and is reversible. A deleted measurement that
 * cannot be explained or recovered is how an audit trail stops being evidence, so the row stays and
 * the trail records who removed it and why.
 */
@Service
public class PatientFactService {

    private static final int MAXIMUM_SOURCE_LABEL = 120;

    private static final int MAXIMUM_REASON = 500;

    private static final String DEFAULT_SOURCE_LABEL = "Manual entry";

    private final PatientFactRepository factRepository;
    private final PatientAccessService patientAccess;
    private final PatientFactCatalogService catalog;
    private final FactValueParser valueParser;
    private final PatientChangeEventRecorder changeEvents;
    private final PatientResponseMapper mapper;
    private final EntityManager entityManager;

    public PatientFactService(
            PatientFactRepository factRepository,
            PatientAccessService patientAccess,
            PatientFactCatalogService catalog,
            FactValueParser valueParser,
            PatientChangeEventRecorder changeEvents,
            PatientResponseMapper mapper,
            EntityManager entityManager) {
        this.factRepository = factRepository;
        this.patientAccess = patientAccess;
        this.catalog = catalog;
        this.valueParser = valueParser;
        this.changeEvents = changeEvents;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    /**
     * Records a new clinical detail.
     *
     * <p>The staleness guard is the <em>patient's</em> timestamp, not a fact's: adding a detail is
     * an edit to the record as a whole, and the client has to have been looking at the current
     * version of it. That also makes the duplicate and pregnancy checks meaningful, since they are
     * evaluated against the facts the user could actually see.
     */
    @Transactional
    public FactResponse create(UUID patientId, PatientFactCreateRequest payload) {
        UUID actorId = SecurityContext.require().getId();
        String catalogKey = requireCatalogKey(payload.catalogKey());
        String sourceLabel = sourceLabel(payload.sourceLabel());
        requireExpected(payload.expectedPatientUpdatedAt(), "expected_patient_updated_at", false);
        FactValue value = valueParser.parse(payload.value());

        Patient patient = patientAccess.requireOwned(patientId, actorId);
        if (PatientAccessService.isStale(
                patient.getUpdatedAt(), payload.expectedPatientUpdatedAt())) {
            throw ApplicationError.conflict(
                    "PATIENT_RECORD_STALE",
                    "This patient record changed after you opened it. "
                            + "Reload before adding a detail.");
        }

        PatientFactCatalogEntry entry =
                catalog.activeEntryByKey(catalogKey)
                        .orElseThrow(
                                () ->
                                        ApplicationError.unprocessable(
                                                "PATIENT_FACT_UNSUPPORTED",
                                                "Choose a supported clinical detail from the catalog.",
                                                "catalog_key"));
        validateAgainstCatalog(entry, value);
        PatientProfileRules.validateValueForPatient(patient, entry, value, null);
        PatientFact duplicate = findDuplicate(patientId, entry, value.effectiveDate(), null);
        if (duplicate != null) {
            throw duplicateFact(entry, duplicate);
        }

        PatientFact fact = new PatientFact(patientId, entry.factType(), entry.concept());
        applyValue(fact, entry, value, sourceLabel);
        PatientFact saved = factRepository.saveAndFlush(fact);

        // The trail entry is captured before the row is read back, so it reports the value as the
        // client sent it. The edit path captures after the read-back and therefore reports the
        // column's scale; both match the Python ordering.
        changeEvents.record(
                patientId,
                actorId,
                "fact_created",
                "fact",
                saved.getId(),
                null,
                PatientChangeEventRecorder.factPayload(saved),
                null);
        entityManager.refresh(saved);
        return mapper.toFact(saved);
    }

    /**
     * Replaces the value of an existing detail.
     *
     * <p>The whole value is replaced rather than merged: a clinical reading is not a set of
     * independently editable parts, and letting a caller change the assertion without restating the
     * measurement is how a "present 7.4" silently becomes an "unknown 7.4".
     */
    @Transactional
    public FactResponse update(UUID patientId, UUID factId, PatientFactUpdateRequest payload) {
        UUID actorId = SecurityContext.require().getId();
        String sourceLabel = sourceLabel(payload.sourceLabel());
        requireExpected(payload.expectedFactUpdatedAt(), "expected_fact_updated_at", false);
        FactValue value = valueParser.parse(payload.value());

        Patient patient = patientAccess.requireOwned(patientId, actorId);
        PatientFact fact =
                factRepository
                        .findByIdAndPatientIdAndVoidedAtIsNull(factId, patientId)
                        .orElseThrow(
                                () ->
                                        ApplicationError.notFound(
                                                "FACT_NOT_FOUND", "Patient fact was not found."));
        PatientFactCatalogEntry entry =
                catalog.activeEntryByFact(fact.getFactType(), fact.getConcept())
                        .orElseThrow(
                                () ->
                                        ApplicationError.unprocessable(
                                                "PATIENT_FACT_UNSUPPORTED",
                                                "This legacy detail is not available in the "
                                                        + "controlled catalog.",
                                                "fact_id"));
        if (PatientAccessService.isStale(fact.getUpdatedAt(), payload.expectedFactUpdatedAt())) {
            throw ApplicationError.conflict(
                    "PATIENT_RECORD_STALE",
                    "This clinical detail changed after you opened it. Reload before saving.");
        }
        validateAgainstCatalog(entry, value);
        PatientProfileRules.validateValueForPatient(patient, entry, value, fact);
        PatientFact duplicate = findDuplicate(patientId, entry, value.effectiveDate(), factId);
        if (duplicate != null) {
            throw duplicateFact(entry, duplicate);
        }

        Map<String, Object> before = PatientChangeEventRecorder.factPayload(fact);
        applyValue(fact, entry, value, sourceLabel);
        // Python's UPDATE always sets `updated_at`, even when the submitted value is identical, so
        // the column is touched here rather than left to Hibernate's dirty check.
        fact.setUpdatedAt(TimestampedEntity.nowUtc());
        PatientFact saved = factRepository.saveAndFlush(fact);
        entityManager.refresh(saved);
        changeEvents.record(
                patientId,
                actorId,
                "fact_updated",
                "fact",
                saved.getId(),
                before,
                PatientChangeEventRecorder.factPayload(saved),
                null);
        return mapper.toFact(saved);
    }

    /**
     * Removes a detail by voiding it.
     *
     * <p>The reason is mandatory and is kept both on the row and on the trail entry. The row itself
     * survives, so the removal can be reviewed and undone.
     */
    @Transactional
    public void voidFact(UUID patientId, UUID factId, PatientFactVoidRequest payload) {
        UUID actorId = SecurityContext.require().getId();
        String reason = requireReason(payload);
        requireExpected(payload.expectedFactUpdatedAt(), "expected_fact_updated_at", true);

        patientAccess.requireOwned(patientId, actorId);
        PatientFact fact =
                factRepository
                        .findByIdAndPatientIdAndVoidedAtIsNull(factId, patientId)
                        .orElseThrow(
                                () ->
                                        ApplicationError.notFound(
                                                "PATIENT_FACT_ALREADY_REMOVED",
                                                "This clinical detail is already removed or was "
                                                        + "not found."));
        if (PatientAccessService.isStale(fact.getUpdatedAt(), payload.expectedFactUpdatedAt())) {
            throw ApplicationError.conflict(
                    "PATIENT_RECORD_STALE",
                    "This clinical detail changed after you opened it. Reload before removing.");
        }

        Map<String, Object> before = PatientChangeEventRecorder.factPayload(fact);
        fact.setVoidedAt(TimestampedEntity.nowUtc());
        fact.setVoidReason(reason);
        fact.setVoidedById(actorId);
        PatientFact saved = factRepository.saveAndFlush(fact);
        entityManager.refresh(saved);
        changeEvents.record(
                patientId,
                actorId,
                "fact_voided",
                "fact",
                saved.getId(),
                before,
                PatientChangeEventRecorder.factPayload(saved),
                reason);
    }

    /**
     * Brings a removed detail back.
     *
     * <p>Restoring is refused when an equivalent active detail already exists, because the
     * duplicate rule has to hold however a row becomes active. The check uses the fact's own
     * effective date rather than a submitted one - the request carries no body, since a restore
     * reinstates what was recorded rather than re-authoring it.
     */
    @Transactional
    public FactResponse restore(UUID patientId, UUID factId) {
        UUID actorId = SecurityContext.require().getId();
        patientAccess.requireOwned(patientId, actorId);
        PatientFact fact =
                factRepository
                        .findByIdAndPatientId(factId, patientId)
                        .orElseThrow(
                                () ->
                                        ApplicationError.notFound(
                                                "FACT_NOT_FOUND", "Patient fact was not found."));
        if (fact.getVoidedAt() == null) {
            throw ApplicationError.conflict(
                    "PATIENT_FACT_RESTORE_CONFLICT", "This clinical detail is already active.");
        }
        PatientFactCatalogEntry entry =
                catalog.activeEntryByFact(fact.getFactType(), fact.getConcept())
                        .orElseThrow(
                                () ->
                                        ApplicationError.unprocessable(
                                                "PATIENT_FACT_UNSUPPORTED",
                                                "This legacy detail is no longer available in the "
                                                        + "controlled catalog.",
                                                "fact_id"));
        PatientFact duplicate = findDuplicate(patientId, entry, fact.getEffectiveDate(), null);
        if (duplicate != null) {
            throw new ApplicationError(
                    "PATIENT_FACT_RESTORE_CONFLICT",
                    entry.displayLabel() + " is already active. Edit it instead.",
                    409,
                    null,
                    List.of(Map.of("fact_id", duplicate.getId().toString())));
        }

        Map<String, Object> before = PatientChangeEventRecorder.factPayload(fact);
        fact.setVoidedAt(null);
        fact.setVoidReason(null);
        fact.setVoidedById(null);
        PatientFact saved = factRepository.saveAndFlush(fact);
        entityManager.refresh(saved);
        changeEvents.record(
                patientId,
                actorId,
                "fact_restored",
                "fact",
                saved.getId(),
                before,
                PatientChangeEventRecorder.factPayload(saved),
                null);
        return mapper.toFact(saved);
    }

    /**
     * Port of {@code catalog_fact_values}: checks a submitted value against its catalog entry.
     *
     * <p>Two things can be wrong. The value's shape may not be the one the concept expects - a
     * status answer for a blood test - and the assertion may not be one the concept admits, which
     * is how "absent" is kept out of a measurement.
     */
    private void validateAgainstCatalog(PatientFactCatalogEntry entry, FactValue value) {
        if (!entry.inputKind().equals(value.inputKind())) {
            throw ApplicationError.unprocessable(
                    "PATIENT_FACT_VALUE_INVALID",
                    entry.displayLabel() + " requires a " + entry.inputKind() + " value.",
                    "value.input_kind");
        }
        if (!entry.allowedAssertions().contains(value.assertion().value())) {
            throw ApplicationError.unprocessable(
                    "PATIENT_FACT_VALUE_INVALID",
                    "The selected status is not supported for " + entry.displayLabel() + ".",
                    "value.assertion");
        }
    }

    /**
     * Writes the validated value onto the row.
     *
     * <p>The unit comes from the catalog, never from the request: a caller cannot record 7.4 in a
     * unit of their choosing and have the engine compare it against a threshold expressed in
     * another. Non-numeric shapes clear the numeric columns, so an edit from a measurement to a
     * status answer does not leave a stale reading behind.
     */
    private void applyValue(
            PatientFact fact, PatientFactCatalogEntry entry, FactValue value, String sourceLabel) {
        fact.setFactType(entry.factType());
        fact.setConcept(entry.concept());
        fact.setValueText(null);
        fact.setAssertion(value.assertion());
        fact.setEffectiveDate(value.effectiveDate());
        fact.setSourceLabel(sourceLabel);
        if (FactValue.NUMERIC.equals(value.inputKind())) {
            fact.setValueNumeric(value.valueNumeric());
            fact.setUnit(entry.fixedUnit());
        } else {
            fact.setValueNumeric(null);
            fact.setUnit(null);
        }
    }

    /**
     * Port of {@code active_duplicate_fact_query}.
     *
     * <p>What counts as a duplicate depends on the concept. A condition or medication is a standing
     * answer, so one active row per concept is the limit whatever date it carries. A measurement is
     * a point in time, so it additionally keys on the effective date - the same test on two
     * different days is not a duplicate, but twice on one day is.
     */
    private PatientFact findDuplicate(
            UUID patientId,
            PatientFactCatalogEntry entry,
            LocalDate effectiveDate,
            UUID excludeFactId) {
        List<PatientFact> candidates =
                FactValue.NUMERIC.equals(entry.inputKind())
                        ? (effectiveDate == null
                                ? factRepository
                                        .findByPatientIdAndFactTypeAndConceptAndVoidedAtIsNullAndEffectiveDateIsNullOrderByCreatedAtDesc(
                                                patientId, entry.factType(), entry.concept())
                                : factRepository
                                        .findByPatientIdAndFactTypeAndConceptAndVoidedAtIsNullAndEffectiveDateOrderByCreatedAtDesc(
                                                patientId, entry.factType(), entry.concept(), effectiveDate))
                        : factRepository
                                .findByPatientIdAndFactTypeAndConceptAndVoidedAtIsNullOrderByCreatedAtDesc(
                                        patientId, entry.factType(), entry.concept());
        for (PatientFact candidate : candidates) {
            if (excludeFactId == null || !excludeFactId.equals(candidate.getId())) {
                return candidate;
            }
        }
        return null;
    }

    private static ApplicationError duplicateFact(
            PatientFactCatalogEntry entry, PatientFact duplicate) {
        return new ApplicationError(
                "PATIENT_FACT_DUPLICATE",
                entry.displayLabel() + " already exists. Edit the existing detail instead.",
                409,
                "catalog_key",
                List.of(
                        Map.of(
                                "fact_id", duplicate.getId().toString(),
                                "catalog_key", entry.key(),
                                "display_label", entry.displayLabel())));
    }

    /** {@code catalog_key: str = Field(pattern=r"^[a-z0-9_]+$", min_length=1, max_length=80)}. */
    private static String requireCatalogKey(String catalogKey) {
        if (catalogKey == null) {
            throw PatientValidationErrors.factValueInvalid(
                    "catalog_key", PatientValidationErrors.fieldRequired(), "missing");
        }
        if (catalogKey.isEmpty()) {
            throw PatientValidationErrors.factValueInvalid(
                    "catalog_key", PatientValidationErrors.tooShort(1), "string_too_short");
        }
        if (catalogKey.length() > 80) {
            throw PatientValidationErrors.factValueInvalid(
                    "catalog_key", PatientValidationErrors.tooLong(80), "string_too_long");
        }
        if (!catalogKey.matches("^[a-z0-9_]+$")) {
            throw PatientValidationErrors.factValueInvalid(
                    "catalog_key",
                    "String should match pattern '^[a-z0-9_]+$'",
                    "string_pattern_mismatch");
        }
        return catalogKey;
    }

    /** {@code source_label: str = Field(default="Manual entry", min_length=1, max_length=120)}. */
    private static String sourceLabel(String sourceLabel) {
        if (sourceLabel == null) {
            return DEFAULT_SOURCE_LABEL;
        }
        if (sourceLabel.isEmpty()) {
            throw PatientValidationErrors.factValueInvalid(
                    "source_label", PatientValidationErrors.tooShort(1), "string_too_short");
        }
        if (sourceLabel.length() > MAXIMUM_SOURCE_LABEL) {
            throw PatientValidationErrors.factValueInvalid(
                    "source_label",
                    PatientValidationErrors.tooLong(MAXIMUM_SOURCE_LABEL),
                    "string_too_long");
        }
        return sourceLabel;
    }

    /**
     * The removal reason, whitespace-collapsed as {@code " ".join(value.split())} before its length
     * is judged - so a body of nothing but spaces is rejected rather than stored.
     */
    private static String requireReason(PatientFactVoidRequest payload) {
        String reason = PatientDataFormats.collapse(payload == null ? null : payload.reason());
        if (reason == null) {
            throw PatientValidationErrors.removalReasonRequired(
                    "reason", PatientValidationErrors.fieldRequired(), "missing");
        }
        if (reason.isEmpty()) {
            throw PatientValidationErrors.removalReasonRequired(
                    "reason", PatientValidationErrors.tooShort(1), "string_too_short");
        }
        if (reason.length() > MAXIMUM_REASON) {
            throw PatientValidationErrors.removalReasonRequired(
                    "reason", PatientValidationErrors.tooLong(MAXIMUM_REASON), "string_too_long");
        }
        return reason;
    }

    /**
     * The staleness guard is required on every fact write.
     *
     * <p>A removal reports the failure as a missing reason, because Python's handler mapped every
     * validation error on a fact DELETE to that one code regardless of the field at fault.
     */
    private static void requireExpected(
            OffsetDateTime expected, String field, boolean removal) {
        if (expected != null) {
            return;
        }
        if (removal) {
            throw PatientValidationErrors.removalReasonRequired(
                    field, PatientValidationErrors.fieldRequired(), "missing");
        }
        throw PatientValidationErrors.factValueInvalid(
                field, PatientValidationErrors.fieldRequired(), "missing");
    }
}
