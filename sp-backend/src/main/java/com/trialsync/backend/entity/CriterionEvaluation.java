package com.trialsync.backend.entity;

import java.util.UUID;

import org.hibernate.annotations.Type;

import com.trialsync.backend.domain.model.CriterionKind;
import com.trialsync.backend.domain.model.CriterionResult;
import com.trialsync.backend.entity.type.CriterionKindUserType;
import com.trialsync.backend.entity.type.CriterionResultUserType;
import com.trialsync.backend.entity.type.JsonStringUserType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * The stored outcome of one criterion inside a screening, with the evidence that produced it.
 *
 * <p>The criterion's order, kind and source text are copied in so the saved result reads the same
 * even if the draft workflow later changes; the criterion itself cannot be deleted while an
 * evaluation cites it, because {@code criterion_evaluations_criterion_id_fkey} is
 * {@code ON DELETE RESTRICT}.
 *
 * <p>{@code truth} and {@code reason_code} are {@code varchar}, not PostgreSQL enums, even though
 * the domain layer models them as {@code TruthValue} and {@code ReasonCode}.
 */
@Entity
@Table(name = "criterion_evaluations")
public class CriterionEvaluation extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "screening_id", nullable = false, columnDefinition = "uuid")
    private UUID screeningId;

    @Column(name = "criterion_id", nullable = false, columnDefinition = "uuid")
    private UUID criterionId;

    @Column(name = "criterion_order", nullable = false)
    private int criterionOrder;

    @Type(CriterionKindUserType.class)
    @Column(name = "criterion_kind", nullable = false, columnDefinition = "criterion_kind")
    private CriterionKind criterionKind;

    @Column(name = "criterion_source_text", nullable = false, columnDefinition = "text")
    private String criterionSourceText;

    @Type(CriterionResultUserType.class)
    @Column(name = "result", nullable = false, columnDefinition = "evaluation_result")
    private CriterionResult result;

    @Column(name = "truth", nullable = false, length = 16)
    private String truth;

    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;

    @Column(name = "canonical_explanation", nullable = false, columnDefinition = "text")
    private String canonicalExplanation;

    @Type(JsonStringUserType.class)
    @Column(name = "evidence_json", nullable = false, columnDefinition = "json")
    private String evidenceJson;

    @Type(JsonStringUserType.class)
    @Column(name = "rejected_evidence_json", nullable = false, columnDefinition = "json")
    private String rejectedEvidenceJson;

    @Type(JsonStringUserType.class)
    @Column(name = "missing_information_json", nullable = false, columnDefinition = "json")
    private String missingInformationJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screening_id", insertable = false, updatable = false)
    private Screening screening;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criterion_id", insertable = false, updatable = false)
    private Criterion criterion;

    protected CriterionEvaluation() {
        // Required by JPA.
    }

    public CriterionEvaluation(UUID screeningId, UUID criterionId, int criterionOrder) {
        this.screeningId = screeningId;
        this.criterionId = criterionId;
        this.criterionOrder = criterionOrder;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getScreeningId() {
        return screeningId;
    }

    public void setScreeningId(UUID screeningId) {
        this.screeningId = screeningId;
    }

    public UUID getCriterionId() {
        return criterionId;
    }

    public void setCriterionId(UUID criterionId) {
        this.criterionId = criterionId;
    }

    public int getCriterionOrder() {
        return criterionOrder;
    }

    public void setCriterionOrder(int criterionOrder) {
        this.criterionOrder = criterionOrder;
    }

    public CriterionKind getCriterionKind() {
        return criterionKind;
    }

    public void setCriterionKind(CriterionKind criterionKind) {
        this.criterionKind = criterionKind;
    }

    public String getCriterionSourceText() {
        return criterionSourceText;
    }

    public void setCriterionSourceText(String criterionSourceText) {
        this.criterionSourceText = criterionSourceText;
    }

    public CriterionResult getResult() {
        return result;
    }

    public void setResult(CriterionResult result) {
        this.result = result;
    }

    public String getTruth() {
        return truth;
    }

    public void setTruth(String truth) {
        this.truth = truth;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getCanonicalExplanation() {
        return canonicalExplanation;
    }

    public void setCanonicalExplanation(String canonicalExplanation) {
        this.canonicalExplanation = canonicalExplanation;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }

    public void setEvidenceJson(String evidenceJson) {
        this.evidenceJson = evidenceJson;
    }

    public String getRejectedEvidenceJson() {
        return rejectedEvidenceJson;
    }

    public void setRejectedEvidenceJson(String rejectedEvidenceJson) {
        this.rejectedEvidenceJson = rejectedEvidenceJson;
    }

    public String getMissingInformationJson() {
        return missingInformationJson;
    }

    public void setMissingInformationJson(String missingInformationJson) {
        this.missingInformationJson = missingInformationJson;
    }

    public Screening getScreening() {
        return screening;
    }

    public Criterion getCriterion() {
        return criterion;
    }
}
