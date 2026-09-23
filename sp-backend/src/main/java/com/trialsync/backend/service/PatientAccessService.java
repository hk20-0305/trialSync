package com.trialsync.backend.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.entity.Patient;
import com.trialsync.backend.repository.PatientRepository;

/** Ownership and staleness guards shared by every patient-scoped endpoint. */
@Service
public class PatientAccessService {

    private final PatientRepository patientRepository;

    public PatientAccessService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    /**
     * Loads a patient the caller owns.
     *
     * <p>Another account's patient answers 404, not 403: the response must not reveal that the
     * identifier exists.
     */
    public Patient requireOwned(UUID patientId, UUID ownerId) {
        return patientRepository
                .findByIdAndOwnerId(patientId, ownerId)
                .orElseThrow(
                        () -> ApplicationError.notFound("PATIENT_NOT_FOUND", "Patient was not found."));
    }

    /**
     * True when the row moved on since the client read it.
     *
     * <p>Compared as instants, which is how Python compares two aware {@code datetime} values and
     * how PostgreSQL compares two {@code timestamptz} values. A client that echoes back the exact
     * timestamp it was given therefore matches regardless of how the offset was spelled.
     */
    public static boolean isStale(OffsetDateTime stored, OffsetDateTime expected) {
        return expected == null || stored == null || !stored.isEqual(expected);
    }

    /**
     * The conflict raised when a patient profile moved on before the edit was submitted.
     *
     * <p>Carries both timestamps so the client can show what it was holding against what is now
     * stored.
     */
    public static ApplicationError profileStale(OffsetDateTime expected, OffsetDateTime current) {
        return new ApplicationError(
                "PATIENT_RECORD_STALE",
                "This patient profile changed after you opened it. "
                        + "Reload and review the latest values.",
                409,
                null,
                List.of(
                        linkedDetails(
                                "expected_updated_at",
                                PatientDataFormats.isoformat(expected),
                                "current_updated_at",
                                PatientDataFormats.isoformat(current))));
    }

    private static Map<String, Object> linkedDetails(
            String firstKey, Object firstValue, String secondKey, Object secondValue) {
        java.util.LinkedHashMap<String, Object> details = new java.util.LinkedHashMap<>();
        details.put(firstKey, firstValue);
        details.put(secondKey, secondValue);
        return details;
    }
}
