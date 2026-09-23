package com.trialsync.backend.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A single retrieved or explained criterion item within an eligibility RAG run.
 */
@Entity
@Table(name = "eligibility_rag_results")
public class EligibilityRagResult extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "rag_run_id", nullable = false, columnDefinition = "uuid")
    private UUID ragRunId;

    @Column(name = "criterion_id", columnDefinition = "uuid")
    private UUID criterionId;

    @Column(name = "rank", nullable = false)
    private int rank;

    @Column(name = "similarity_score", nullable = false)
    private double similarityScore = 0.0;

    @Column(name = "criterion_kind", length = 64)
    private String criterionKind;

    @Column(name = "source_text", nullable = false, columnDefinition = "text")
    private String sourceText;

    @Column(name = "explanation", columnDefinition = "text")
    private String explanation;

    @Column(name = "citation", length = 255)
    private String citation;

    @Column(name = "provenance_valid", nullable = false)
    private boolean provenanceValid = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rag_run_id", insertable = false, updatable = false)
    private EligibilityRagRun ragRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criterion_id", insertable = false, updatable = false)
    private Criterion criterion;

    protected EligibilityRagResult() {
        // Required by JPA.
    }

    public EligibilityRagResult(UUID ragRunId, UUID criterionId, int rank, double similarityScore,
                                String criterionKind, String sourceText) {
        this.ragRunId = ragRunId;
        this.criterionId = criterionId;
        this.rank = rank;
        this.similarityScore = similarityScore;
        this.criterionKind = criterionKind;
        this.sourceText = sourceText;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRagRunId() {
        return ragRunId;
    }

    public void setRagRunId(UUID ragRunId) {
        this.ragRunId = ragRunId;
    }

    public UUID getCriterionId() {
        return criterionId;
    }

    public void setCriterionId(UUID criterionId) {
        this.criterionId = criterionId;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
    }

    public String getCriterionKind() {
        return criterionKind;
    }

    public void setCriterionKind(String criterionKind) {
        this.criterionKind = criterionKind;
    }

    public String getSourceText() {
        return sourceText;
    }

    public void setSourceText(String sourceText) {
        this.sourceText = sourceText;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getCitation() {
        return citation;
    }

    public void setCitation(String citation) {
        this.citation = citation;
    }

    public boolean isProvenanceValid() {
        return provenanceValid;
    }

    public void setProvenanceValid(boolean provenanceValid) {
        this.provenanceValid = provenanceValid;
    }

    public EligibilityRagRun getRagRun() {
        return ragRun;
    }

    public void setRagRun(EligibilityRagRun ragRun) {
        this.ragRun = ragRun;
        if (ragRun != null) {
            this.ragRunId = ragRun.getId();
        }
    }

    public Criterion getCriterion() {
        return criterion;
    }

    public void setCriterion(Criterion criterion) {
        this.criterion = criterion;
    }
}
