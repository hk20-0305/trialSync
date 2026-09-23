package com.trialsync.backend.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trialsync.backend.domain.model.Assertion;
import com.trialsync.backend.domain.model.FactType;
import com.trialsync.backend.dto.patient.FactResponse;
import com.trialsync.backend.dto.patient.PatientChangeEventResponse;
import com.trialsync.backend.dto.patient.PatientConsistencyIssueResponse;
import com.trialsync.backend.dto.patient.PatientResponse;
import com.trialsync.backend.dto.patient.UnsupportedDetailResponse;
import com.trialsync.backend.entity.Patient;
import com.trialsync.backend.entity.PatientChangeEvent;
import com.trialsync.backend.entity.PatientFact;
import com.trialsync.backend.entity.PatientUnsupportedDetail;

/**
 * Turns persisted patient rows into the response contract.
 *
 * <p>Called from inside the service transaction, because a patient's facts and review items are
 * lazily loaded.
 */
@Component
public class PatientResponseMapper {

    /** The concept the pregnancy consistency rules key on. */
    private static final String PREGNANCY_CONCEPT = "pregnancy";

    private static final String MALE = "male";

    private final ObjectMapper objectMapper;

    public PatientResponseMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Port of {@code PatientRead}, including the issues it derives on read. */
    public PatientResponse toPatient(Patient patient) {
        List<FactResponse> facts = new ArrayList<>();
        for (PatientFact fact : patient.getFacts()) {
            facts.add(toFact(fact));
        }
        List<UnsupportedDetailResponse> details = new ArrayList<>();
        for (PatientUnsupportedDetail detail : patient.getUnsupportedDetails()) {
            details.add(toUnsupportedDetail(detail));
        }
        return new PatientResponse(
                patient.getId(),
                patient.getExternalId(),
                patient.getDisplayName(),
                patient.getDateOfBirth(),
                patient.getSex(),
                patient.getCreatedAt(),
                patient.getUpdatedAt(),
                facts,
                details,
                consistencyIssues(patient));
    }

    /** Port of {@code FactRead}. */
    public FactResponse toFact(PatientFact fact) {
        return new FactResponse(
                fact.getFactType(),
                fact.getConcept(),
                fact.getValueNumeric() == null ? null : fact.getValueNumeric().toPlainString(),
                fact.getValueText(),
                fact.getUnit(),
                fact.getAssertion(),
                fact.getEffectiveDate(),
                fact.getSourceLabel(),
                fact.getId(),
                fact.getPatientId(),
                fact.getCreatedAt(),
                fact.getUpdatedAt(),
                fact.getVoidedAt(),
                fact.getVoidReason());
    }

    /** Port of {@code UnsupportedDetailRead}. */
    public UnsupportedDetailResponse toUnsupportedDetail(PatientUnsupportedDetail detail) {
        return new UnsupportedDetailResponse(
                detail.getCategory(),
                detail.getLabel(),
                detail.getContext(),
                detail.getSourceLabel(),
                detail.getId(),
                detail.getPatientId(),
                detail.getCreatedAt(),
                detail.getUpdatedAt());
    }

    /** Port of {@code PatientChangeEventRead}. */
    public PatientChangeEventResponse toChangeEvent(PatientChangeEvent event) {
        return new PatientChangeEventResponse(
                event.getId(),
                event.getPatientId(),
                event.getActorId(),
                event.getEventType(),
                event.getEntityType(),
                event.getEntityId(),
                event.getReason(),
                readJson(event.getBeforeJson()),
                readJson(event.getAfterJson()),
                event.getCreatedAt());
    }

    /**
     * Port of {@code add_pregnancy_consistency_issues}.
     *
     * <p>These are reported, never enforced: a record can be saved in an inconsistent state and the
     * UI surfaces it, because a synthetic patient may legitimately be mid-edit. The blocking
     * variant of the same rule lives on the write paths, where a pregnancy assertion and a male
     * biological sex cannot be introduced against each other.
     *
     * <p>Only the patient's active facts are considered - a voided pregnancy no longer says
     * anything about the record. The first matching fact wins, and the list is ordered by creation,
     * so the earliest recorded pregnancy is the one reported.
     */
    private List<PatientConsistencyIssueResponse> consistencyIssues(Patient patient) {
        PatientFact pregnancy = null;
        for (PatientFact fact : patient.getFacts()) {
            if (fact.getFactType() == FactType.CONDITION
                    && PREGNANCY_CONCEPT.equals(fact.getConcept())
                    && fact.getAssertion() == Assertion.PRESENT) {
                pregnancy = fact;
                break;
            }
        }
        if (pregnancy == null) {
            return List.of();
        }
        if (MALE.equals(patient.getSex())) {
            return List.of(
                    new PatientConsistencyIssueResponse(
                            "PATIENT_PREGNANCY_SEX_CONFLICT",
                            "conflict",
                            "Pregnancy is recorded as Pregnant for a patient whose "
                                    + "biological sex is Male.",
                            "pregnancy",
                            pregnancy.getId()));
        }
        if (patient.getSex() == null) {
            return List.of(
                    new PatientConsistencyIssueResponse(
                            "PATIENT_SEX_NOT_RECORDED_FOR_PREGNANCY",
                            "warning",
                            "Pregnancy is recorded as Pregnant, but biological sex "
                                    + "is not recorded.",
                            "sex",
                            pregnancy.getId()));
        }
        return List.of();
    }

    /**
     * Re-reads a stored activity payload so it is echoed as a JSON object rather than as a string.
     *
     * <p>The column holds the document as text to keep its key order, so it has to be parsed back
     * on the way out.
     */
    private JsonNode readJson(String document) {
        if (document == null) {
            return null;
        }
        try {
            return objectMapper.readTree(document);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "A stored patient activity payload could not be parsed", exception);
        }
    }
}
