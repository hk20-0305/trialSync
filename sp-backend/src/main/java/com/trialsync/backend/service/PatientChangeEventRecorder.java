package com.trialsync.backend.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trialsync.backend.entity.Patient;
import com.trialsync.backend.entity.PatientChangeEvent;
import com.trialsync.backend.entity.PatientFact;
import com.trialsync.backend.repository.PatientChangeEventRepository;

/**
 * Writes the immutable activity trail for a patient record.
 *
 * <p>Every mutation of a patient's profile or clinical details appends one entry. Entries are never
 * updated or deleted, which is why the payloads are frozen as JSON documents rather than as foreign
 * keys: the trail has to keep reading correctly after the row it describes has been edited again or
 * the catalog concept behind it has been retired.
 *
 * <p>The payloads are built as insertion-ordered maps and serialised straight to text, so the
 * stored document keeps the key order the Python dictionaries had. Timestamps and decimals inside
 * them are pre-rendered to strings for the same reason.
 */
@Service
public class PatientChangeEventRecorder {

    private final PatientChangeEventRepository changeEventRepository;
    private final ObjectMapper objectMapper;

    public PatientChangeEventRecorder(
            PatientChangeEventRepository changeEventRepository, ObjectMapper objectMapper) {
        this.changeEventRepository = changeEventRepository;
        this.objectMapper = objectMapper;
    }

    /** Port of {@code record_change}. */
    public void record(
            UUID patientId,
            UUID actorId,
            String eventType,
            String entityType,
            UUID entityId,
            Map<String, Object> before,
            Map<String, Object> after,
            String reason) {
        PatientChangeEvent event =
                new PatientChangeEvent(patientId, actorId, eventType, entityType);
        event.setEntityId(entityId);
        event.setReason(reason);
        event.setBeforeJson(serialize(before));
        event.setAfterJson(serialize(after));
        changeEventRepository.save(event);
    }

    /**
     * Port of {@code profile_event_payload}. Carries only the three clinically meaningful profile
     * fields; the synthetic external identifier is not part of the trail.
     */
    public static Map<String, Object> profilePayload(Patient patient) {
        return profilePayload(
                patient.getDisplayName(),
                patient.getDateOfBirth() == null ? null : patient.getDateOfBirth().toString(),
                patient.getSex());
    }

    /**
     * The same payload assembled from already-resolved values, for the "after" side of a profile
     * edit where an unsupplied field has to fall back to what the record held before.
     */
    public static Map<String, Object> profilePayload(
            String displayName, String dateOfBirth, String sex) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("display_name", displayName);
        payload.put("date_of_birth", dateOfBirth);
        payload.put("sex", sex);
        return payload;
    }

    /**
     * Port of {@code fact_event_payload}.
     *
     * <p>{@code value_numeric} is rendered with {@code str(Decimal)} semantics, so the scale of the
     * value at the moment of capture is preserved. That is visible in the trail: a create event
     * records the payload before the row is read back, so it shows the submitted "7.4", while an
     * edit records it afterwards and shows the column's "7.400000".
     */
    public static Map<String, Object> factPayload(PatientFact fact) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", fact.getId().toString());
        payload.put("fact_type", fact.getFactType().value());
        payload.put("concept", fact.getConcept());
        payload.put(
                "value_numeric",
                fact.getValueNumeric() == null ? null : fact.getValueNumeric().toPlainString());
        payload.put("value_text", fact.getValueText());
        payload.put("unit", fact.getUnit());
        payload.put("assertion", fact.getAssertion().value());
        payload.put(
                "effective_date",
                fact.getEffectiveDate() == null ? null : fact.getEffectiveDate().toString());
        payload.put("source_label", fact.getSourceLabel());
        payload.put("voided_at", PatientDataFormats.isoformat(fact.getVoidedAt()));
        payload.put("void_reason", fact.getVoidReason());
        return payload;
    }

    private String serialize(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("A patient activity payload could not be serialized", exception);
        }
    }
}
