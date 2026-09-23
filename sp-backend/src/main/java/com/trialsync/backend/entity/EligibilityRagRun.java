package com.trialsync.backend.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Records an eligibility RAG query, retrieval, and explanation run against an approved trial version.
 */
@Entity
@Table(name = "eligibility_rag_runs")
public class EligibilityRagRun extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "trial_version_id", nullable = false, columnDefinition = "uuid")
    private UUID trialVersionId;

    @Column(name = "owner_id", columnDefinition = "uuid")
    private UUID ownerId;

    @Column(name = "query_text", nullable = false, columnDefinition = "text")
    private String queryText;

    @Column(name = "run_type", nullable = false, length = 32)
    private String runType = "EXPLAIN";

    @Column(name = "model", length = 64)
    private String model;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "COMPLETED";

    @Column(name = "insufficient_evidence", nullable = false)
    private boolean insufficientEvidence = false;

    @Column(name = "summary_text", columnDefinition = "text")
    private String summaryText;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trial_version_id", insertable = false, updatable = false)
    private TrialVersion trialVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", insertable = false, updatable = false)
    private User owner;

    @OneToMany(mappedBy = "ragRun", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("rank ASC")
    private List<EligibilityRagResult> results = new ArrayList<>();

    protected EligibilityRagRun() {
        // Required by JPA.
    }

    public EligibilityRagRun(UUID trialVersionId, String queryText, String runType) {
        this.trialVersionId = trialVersionId;
        this.queryText = queryText;
        this.runType = runType;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTrialVersionId() {
        return trialVersionId;
    }

    public void setTrialVersionId(UUID trialVersionId) {
        this.trialVersionId = trialVersionId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public String getQueryText() {
        return queryText;
    }

    public void setQueryText(String queryText) {
        this.queryText = queryText;
    }

    public String getRunType() {
        return runType;
    }

    public void setRunType(String runType) {
        this.runType = runType;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isInsufficientEvidence() {
        return insufficientEvidence;
    }

    public void setInsufficientEvidence(boolean insufficientEvidence) {
        this.insufficientEvidence = insufficientEvidence;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public TrialVersion getTrialVersion() {
        return trialVersion;
    }

    public void setTrialVersion(TrialVersion trialVersion) {
        this.trialVersion = trialVersion;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public List<EligibilityRagResult> getResults() {
        return results;
    }

    public void setResults(List<EligibilityRagResult> results) {
        this.results = results;
    }

    public void addResult(EligibilityRagResult result) {
        results.add(result);
        result.setRagRun(this);
    }
}
