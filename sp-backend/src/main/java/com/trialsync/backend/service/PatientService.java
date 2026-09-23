package com.trialsync.backend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.dto.patient.PatientChangeEventResponse;
import com.trialsync.backend.dto.patient.PatientCreateRequest;
import com.trialsync.backend.dto.patient.PatientResponse;
import com.trialsync.backend.dto.patient.PatientUpdateRequest;
import com.trialsync.backend.dto.patient.UnsupportedDetailCreateRequest;
import com.trialsync.backend.dto.patient.UnsupportedDetailResponse;
import com.trialsync.backend.dto.patient.UnsupportedDetailUpdateRequest;
import com.trialsync.backend.entity.Patient;
import com.trialsync.backend.entity.PatientChangeEvent;
import com.trialsync.backend.entity.PatientFact;
import com.trialsync.backend.entity.PatientUnsupportedDetail;
import com.trialsync.backend.entity.TimestampedEntity;
import com.trialsync.backend.repository.PatientChangeEventRepository;
import com.trialsync.backend.repository.PatientRepository;
import com.trialsync.backend.repository.PatientUnsupportedDetailRepository;
import com.trialsync.backend.security.SecurityContext;

/**
 * The synthetic patient record: create, read, edit, delete, its activity trail and its review items.
 *
 * <p>Every operation is scoped to the signed-in owner. A patient belonging to another account is
 * reported as not found rather than forbidden, so identifiers cannot be probed across tenants.
 *
 * <p>Edits are guarded by {@code expected_updated_at} rather than by a version counter: the client
 * echoes back the timestamp it was shown, and a mismatch means the record moved on underneath it.
 * The comparison is made on the instant, so a client that re-serialises the timestamp with a
 * different offset spelling still matches.
 *
 * <p>Review items - the unsupported details - live here rather than alongside the clinical facts
 * because they are profile-level annotations rather than screening data: the engine never reads one,
 * they carry no catalog concept, and they record no change event.
 */
@Service
public class PatientService {

    /** Bounds from {@code PatientCreate} and {@code PatientUpdate}. */
    private static final int MAXIMUM_DISPLAY_NAME = 120;

    private static final int MAXIMUM_EXTERNAL_ID = 64;

    /** Bounds from {@code UnsupportedDetailCreate} and {@code UnsupportedDetailUpdate}. */
    private static final int MAXIMUM_DETAIL_LABEL = 160;

    private static final int MAXIMUM_DETAIL_CONTEXT = 500;

    private static final int MAXIMUM_DETAIL_SOURCE_LABEL = 120;

    /** {@code source_label: str = Field(default="Manual review item", ...)}. */
    private static final String DEFAULT_SOURCE_LABEL = "Manual review item";

    /** {@code UnsupportedDetailCategory}: broader than the catalog, since it admits "other". */
    private static final List<String> UNSUPPORTED_DETAIL_CATEGORIES =
            List.of("condition", "medication", "observation", "other");

    private final PatientRepository patientRepository;
    private final PatientChangeEventRepository changeEventRepository;
    private final PatientUnsupportedDetailRepository unsupportedDetailRepository;
    private final PatientAccessService patientAccess;
    private final PatientChangeEventRecorder changeEvents;
    private final PatientResponseMapper mapper;
    private final Clock clock;

    public PatientService(
            PatientRepository patientRepository,
            PatientChangeEventRepository changeEventRepository,
            PatientUnsupportedDetailRepository unsupportedDetailRepository,
            PatientAccessService patientAccess,
            PatientChangeEventRecorder changeEvents,
            PatientResponseMapper mapper,
            Clock clock) {
        this.patientRepository = patientRepository;
        this.changeEventRepository = changeEventRepository;
        this.unsupportedDetailRepository = unsupportedDetailRepository;
        this.patientAccess = patientAccess;
        this.changeEvents = changeEvents;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** The caller's most recently touched patients, newest first, capped at 100. */
    @Transactional(readOnly = true)
    public List<PatientResponse> list() {
        UUID ownerId = SecurityContext.require().getId();
        List<PatientResponse> responses = new ArrayList<>();
        for (Patient patient : patientRepository.findTop100ByOwnerIdOrderByUpdatedAtDesc(ownerId)) {
            responses.add(mapper.toPatient(patient));
        }
        return responses;
    }

    /**
     * Creates a synthetic patient.
     *
     * <p>A name already in use does not block the create, it interrupts it: the first attempt
     * answers a conflict naming the existing record, and the client re-sends with
     * {@code confirm_duplicate_name} once the user has decided they are different people. Two
     * synthetic patients may legitimately share a name, so refusing outright would be wrong, but
     * silently creating a near-duplicate is how a screening run ends up against the wrong record.
     */
    @Transactional
    public PatientResponse create(PatientCreateRequest payload) {
        UUID ownerId = SecurityContext.require().getId();
        validateProfile(payload.dateOfBirth(), payload.sex(), payload.externalId());
        validateDisplayName(payload.displayName(), true);

        Patient duplicate =
                firstOrNull(
                        patientRepository.findByOwnerIdAndLoweredDisplayName(
                                ownerId, payload.displayName().strip().toLowerCase(Locale.ROOT)));
        if (duplicate != null && !Boolean.TRUE.equals(payload.confirmDuplicateName())) {
            throw new ApplicationError(
                    "PATIENT_NAME_REVIEW_REQUIRED",
                    "A patient with this name already exists. Review it or continue creating "
                            + "a distinct synthetic record.",
                    409,
                    "display_name",
                    List.of(
                            Map.of(
                                    "patient_id", duplicate.getId().toString(),
                                    "display_name", duplicate.getDisplayName())));
        }

        Patient patient =
                new Patient(
                        ownerId,
                        payload.externalId() == null || payload.externalId().isEmpty()
                                ? generateExternalId()
                                : payload.externalId(),
                        payload.displayName());
        patient.setDateOfBirth(payload.dateOfBirth());
        patient.setSex(payload.sex());

        Patient saved;
        try {
            saved = patientRepository.saveAndFlush(patient);
        } catch (DataIntegrityViolationException exception) {
            throw externalIdExists();
        }
        changeEvents.record(
                saved.getId(),
                ownerId,
                "patient_created",
                "patient",
                saved.getId(),
                null,
                PatientChangeEventRecorder.profilePayload(saved),
                null);
        return mapper.toPatient(saved);
    }

    /** One owned patient, with its active facts, review items and derived consistency issues. */
    @Transactional(readOnly = true)
    public PatientResponse get(UUID patientId) {
        UUID ownerId = SecurityContext.require().getId();
        return mapper.toPatient(patientAccess.requireOwned(patientId, ownerId));
    }

    /** The bounded immutable change history for one owned patient, newest first. */
    @Transactional(readOnly = true)
    public List<PatientChangeEventResponse> activity(UUID patientId) {
        UUID ownerId = SecurityContext.require().getId();
        patientAccess.requireOwned(patientId, ownerId);
        List<PatientChangeEventResponse> responses = new ArrayList<>();
        for (PatientChangeEvent event :
                changeEventRepository.findTop100ByPatientIdOrderByCreatedAtDesc(patientId)) {
            responses.add(mapper.toChangeEvent(event));
        }
        return responses;
    }

    /**
     * Edits the profile.
     *
     * <p>Only the fields the caller actually sent are written, so sending {@code "sex": null}
     * clears the value while omitting {@code sex} leaves it alone. That distinction is why the
     * request is not a record: it has to remember which properties were present in the document.
     */
    @Transactional
    public PatientResponse update(UUID patientId, PatientUpdateRequest payload) {
        UUID ownerId = SecurityContext.require().getId();
        validateProfile(payload.getDateOfBirth(), payload.getSex(), payload.getExternalId());
        validateDisplayName(payload.getDisplayName(), false);
        if (payload.getExpectedUpdatedAt() == null) {
            throw PatientValidationErrors.requestInvalid(
                    "expected_updated_at", PatientValidationErrors.fieldRequired(), "missing");
        }

        Patient patient = patientAccess.requireOwned(patientId, ownerId);
        if (PatientAccessService.isStale(patient.getUpdatedAt(), payload.getExpectedUpdatedAt())) {
            throw PatientAccessService.profileStale(
                    payload.getExpectedUpdatedAt(), patient.getUpdatedAt());
        }

        if (payload.isSexSupplied()
                && PatientProfileRules.MALE.equals(payload.getSex())
                && !PatientProfileRules.MALE.equals(patient.getSex())) {
            PatientFact conflicting = PatientProfileRules.presentPregnancyFact(patient);
            if (conflicting != null) {
                throw PatientProfileRules.sexConflict("sex", conflicting);
            }
        }

        Map<String, Object> before = PatientChangeEventRecorder.profilePayload(patient);
        if (payload.isExternalIdSupplied()) {
            patient.setExternalId(payload.getExternalId());
        }
        if (payload.isDisplayNameSupplied()) {
            patient.setDisplayName(payload.getDisplayName());
        }
        if (payload.isDateOfBirthSupplied()) {
            patient.setDateOfBirth(payload.getDateOfBirth());
        }
        if (payload.isSexSupplied()) {
            patient.setSex(payload.getSex());
        }
        // Python issues the UPDATE unconditionally with `updated_at=func.now()`, so a PATCH that
        // changes nothing still advances the timestamp and invalidates any other tab holding the
        // old one. Touching the column here forces the same write, which Hibernate would otherwise
        // skip for a clean entity.
        patient.setUpdatedAt(TimestampedEntity.nowUtc());

        Patient saved;
        try {
            saved = patientRepository.saveAndFlush(patient);
        } catch (DataIntegrityViolationException exception) {
            throw externalIdExists();
        }
        changeEvents.record(
                patientId,
                ownerId,
                "profile_updated",
                "patient",
                patientId,
                before,
                PatientChangeEventRecorder.profilePayload(saved),
                null);
        return mapper.toPatient(saved);
    }

    /**
     * Removes a patient outright.
     *
     * <p>Unlike a clinical detail, a patient is hard-deleted: the facts, review items and activity
     * trail go with it through the schema's cascades, and any screening snapshot keeps its frozen
     * copy of the data with its patient reference cleared.
     */
    @Transactional
    public void delete(UUID patientId) {
        UUID ownerId = SecurityContext.require().getId();
        patientRepository.delete(patientAccess.requireOwned(patientId, ownerId));
    }

    /**
     * Parks a clinical detail the controlled catalog does not cover.
     *
     * <p>A review item is not screening data - the engine never reads one - so it carries no
     * catalog concept and records no change event. It exists so that importing a document cannot
     * silently discard a finding the catalog has no concept for.
     *
     * <p>The duplicate rule is per patient, per category, on the label ignoring case, and it
     * refuses rather than merges: two identical review items would be two identical pieces of work
     * for whoever triages them.
     */
    @Transactional
    public UnsupportedDetailResponse createUnsupportedDetail(
            UUID patientId, UnsupportedDetailCreateRequest payload) {
        UUID ownerId = SecurityContext.require().getId();
        String category = requireCategory(payload.category(), true);
        String label = requireLabel(payload.label(), true);
        String context = validateContext(payload.context());
        String sourceLabel = requireSourceLabel(payload.sourceLabel());

        patientAccess.requireOwned(patientId, ownerId);
        List<PatientUnsupportedDetail> duplicates =
                unsupportedDetailRepository.findByPatientIdAndCategoryAndLoweredLabel(
                        patientId, category, label.toLowerCase(Locale.ROOT));
        if (!duplicates.isEmpty()) {
            throw new ApplicationError(
                    "PATIENT_UNSUPPORTED_DETAIL_DUPLICATE",
                    "This unsupported detail is already recorded for review.",
                    409,
                    "label",
                    List.of(Map.of("detail_id", duplicates.get(0).getId().toString())));
        }

        PatientUnsupportedDetail detail =
                new PatientUnsupportedDetail(patientId, category, label);
        detail.setContext(context);
        detail.setSourceLabel(sourceLabel);
        return mapper.toUnsupportedDetail(unsupportedDetailRepository.saveAndFlush(detail));
    }

    /**
     * Edits a review item.
     *
     * <p>An edit that carries nothing but the staleness guard performs no write at all, so
     * {@code updated_at} is left where it was and another tab holding the same timestamp stays
     * valid. That is deliberate in Python and is reproduced here rather than collapsed into an
     * unconditional save.
     */
    @Transactional
    public UnsupportedDetailResponse updateUnsupportedDetail(
            UUID patientId, UUID detailId, UnsupportedDetailUpdateRequest payload) {
        UUID ownerId = SecurityContext.require().getId();
        String category =
                payload.isCategorySupplied() ? requireCategory(payload.getCategory(), false) : null;
        String label = payload.isLabelSupplied() ? requireLabel(payload.getLabel(), false) : null;
        String context = payload.isContextSupplied() ? validateContext(payload.getContext()) : null;
        if (payload.getExpectedUpdatedAt() == null) {
            throw PatientValidationErrors.requestInvalid(
                    "expected_updated_at", PatientValidationErrors.fieldRequired(), "missing");
        }

        PatientUnsupportedDetail detail = ownedUnsupportedDetail(patientId, detailId, ownerId);
        if (PatientAccessService.isStale(detail.getUpdatedAt(), payload.getExpectedUpdatedAt())) {
            throw ApplicationError.conflict(
                    "PATIENT_RECORD_STALE",
                    "This review item changed after you opened it. Reload before saving.");
        }
        if (!payload.hasChanges()) {
            return mapper.toUnsupportedDetail(detail);
        }

        if (payload.isCategorySupplied()) {
            detail.setCategory(category);
        }
        if (payload.isLabelSupplied()) {
            detail.setLabel(label);
        }
        if (payload.isContextSupplied()) {
            detail.setContext(context);
        }
        // Python's UPDATE sets `updated_at=func.now()` alongside the changed columns, so a PATCH
        // that rewrites a field with its current value still advances the timestamp.
        detail.setUpdatedAt(TimestampedEntity.nowUtc());
        return mapper.toUnsupportedDetail(unsupportedDetailRepository.saveAndFlush(detail));
    }

    /**
     * Removes a review item outright.
     *
     * <p>Hard-deleted, unlike a clinical fact: a review item is a note about work still to be done,
     * so once it has been dealt with there is nothing to keep. Nothing references it and no change
     * event is written.
     */
    @Transactional
    public void deleteUnsupportedDetail(UUID patientId, UUID detailId) {
        UUID ownerId = SecurityContext.require().getId();
        unsupportedDetailRepository.delete(ownedUnsupportedDetail(patientId, detailId, ownerId));
    }

    /**
     * Port of {@code owned_unsupported_detail}.
     *
     * <p>Ownership of the patient is proved first, so a detail identifier belonging to someone
     * else's patient reports the patient as missing rather than the detail.
     */
    private PatientUnsupportedDetail ownedUnsupportedDetail(
            UUID patientId, UUID detailId, UUID ownerId) {
        patientAccess.requireOwned(patientId, ownerId);
        return unsupportedDetailRepository
                .findByIdAndPatientId(detailId, patientId)
                .orElseThrow(
                        () ->
                                ApplicationError.notFound(
                                        "PATIENT_UNSUPPORTED_DETAIL_NOT_FOUND",
                                        "Unsupported clinical detail was not found."));
    }

    /** {@code UnsupportedDetailCategory}, a closed set of four. */
    private static String requireCategory(String category, boolean required) {
        if (category == null) {
            if (required) {
                throw PatientValidationErrors.requestInvalid(
                        "category", PatientValidationErrors.fieldRequired(), "missing");
            }
            return null;
        }
        if (!UNSUPPORTED_DETAIL_CATEGORIES.contains(category)) {
            throw PatientValidationErrors.requestInvalid(
                    "category",
                    "Input should be 'condition', 'medication', 'observation' or 'other'",
                    "literal_error");
        }
        return category;
    }

    /**
     * {@code label}, normalized before its bounds are judged.
     *
     * <p>A label of nothing but whitespace collapses to null and is rejected as a bad string rather
     * than as an absent one, which is what {@code " ".join(value.split()) or None} produces against
     * a field typed {@code str}.
     */
    private static String requireLabel(String label, boolean required) {
        if (label == null) {
            if (required) {
                throw PatientValidationErrors.requestInvalid(
                        "label", PatientValidationErrors.fieldRequired(), "missing");
            }
            return null;
        }
        String normalized = PatientDataFormats.collapseToNull(label);
        if (normalized == null) {
            throw PatientValidationErrors.requestInvalid(
                    "label", "Input should be a valid string", "string_type");
        }
        if (normalized.length() > MAXIMUM_DETAIL_LABEL) {
            throw PatientValidationErrors.requestInvalid(
                    "label", PatientValidationErrors.tooLong(MAXIMUM_DETAIL_LABEL), "string_too_long");
        }
        return normalized;
    }

    /** {@code context}, optional and nullable, so a blank one is simply cleared. */
    private static String validateContext(String context) {
        String normalized = PatientDataFormats.collapseToNull(context);
        if (normalized != null && normalized.length() > MAXIMUM_DETAIL_CONTEXT) {
            throw PatientValidationErrors.requestInvalid(
                    "context",
                    PatientValidationErrors.tooLong(MAXIMUM_DETAIL_CONTEXT),
                    "string_too_long");
        }
        return normalized;
    }

    /** {@code source_label}, which falls back to its default when the caller omits it. */
    private static String requireSourceLabel(String sourceLabel) {
        if (sourceLabel == null) {
            return DEFAULT_SOURCE_LABEL;
        }
        String normalized = PatientDataFormats.collapseToNull(sourceLabel);
        if (normalized == null) {
            throw PatientValidationErrors.requestInvalid(
                    "source_label", "Input should be a valid string", "string_type");
        }
        if (normalized.length() > MAXIMUM_DETAIL_SOURCE_LABEL) {
            throw PatientValidationErrors.requestInvalid(
                    "source_label",
                    PatientValidationErrors.tooLong(MAXIMUM_DETAIL_SOURCE_LABEL),
                    "string_too_long");
        }
        return normalized;
    }

    /**
     * The validators Pydantic ran before the endpoint body.
     *
     * <p>Order matters. Pydantic reports every field error at once and the handler walks that list
     * looking for the two it remaps, so when a future date of birth and an unsupported sex arrive
     * together the date wins - it is declared first. Checking in the same order reproduces that
     * without collecting errors.
     */
    private void validateProfile(LocalDate dateOfBirth, String sex, String externalId) {
        if (dateOfBirth != null && dateOfBirth.isAfter(LocalDate.now(clock))) {
            throw PatientValidationErrors.dateOfBirthInFuture();
        }
        if (!PatientProfileRules.isSupportedSex(sex)) {
            throw PatientValidationErrors.sexInvalid();
        }
        if (externalId != null && externalId.isEmpty()) {
            throw PatientValidationErrors.requestInvalid(
                    "external_id", PatientValidationErrors.tooShort(1), "string_too_short");
        }
        if (externalId != null && externalId.length() > MAXIMUM_EXTERNAL_ID) {
            throw PatientValidationErrors.requestInvalid(
                    "external_id",
                    PatientValidationErrors.tooLong(MAXIMUM_EXTERNAL_ID),
                    "string_too_long");
        }
    }

    /** {@code display_name} is mandatory on create and optional on edit, with the same bounds. */
    private void validateDisplayName(String displayName, boolean required) {
        if (displayName == null) {
            if (required) {
                throw PatientValidationErrors.requestInvalid(
                        "display_name", PatientValidationErrors.fieldRequired(), "missing");
            }
            return;
        }
        if (displayName.isEmpty()) {
            throw PatientValidationErrors.requestInvalid(
                    "display_name", PatientValidationErrors.tooShort(1), "string_too_short");
        }
        if (displayName.length() > MAXIMUM_DISPLAY_NAME) {
            throw PatientValidationErrors.requestInvalid(
                    "display_name",
                    PatientValidationErrors.tooLong(MAXIMUM_DISPLAY_NAME),
                    "string_too_long");
        }
    }

    /**
     * The synthetic identifier assigned when the caller does not supply one.
     *
     * <p>{@code SYN-} followed by ten upper-case hex characters, matching
     * {@code f"SYN-{uuid.uuid4().hex[:10].upper()}"}. The prefix keeps synthetic records visibly
     * distinct from anything that might later carry a real identifier.
     */
    private static String generateExternalId() {
        String hex = UUID.randomUUID().toString().replace("-", "");
        return "SYN-" + hex.substring(0, 10).toUpperCase(Locale.ROOT);
    }

    private static ApplicationError externalIdExists() {
        return new ApplicationError(
                "PATIENT_EXTERNAL_ID_EXISTS",
                "This synthetic patient ID is already in use.",
                409,
                "external_id");
    }

    private static Patient firstOrNull(List<Patient> patients) {
        return patients.isEmpty() ? null : patients.get(0);
    }
}
