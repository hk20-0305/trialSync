package com.trialsync.backend.entity;

import java.util.UUID;

import org.hibernate.annotations.Type;

import com.trialsync.backend.domain.model.CriterionKind;
import com.trialsync.backend.entity.type.CriterionKindUserType;
import com.trialsync.backend.entity.type.JsonStringUserType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One eligibility criterion of a trial version.
 *
 * <p>{@code normalized_rule} is the deterministic rule the screening engine evaluates. It is null
 * for criteria that were reviewed but could not be expressed as a rule, and such a version cannot
 * be approved.
 */
@Entity
@Table(name = "criteria")
public class Criterion extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "trial_version_id", nullable = false, columnDefinition = "uuid")
    private UUID trialVersionId;

    @Type(CriterionKindUserType.class)
    @Column(name = "kind", nullable = false, columnDefinition = "criterion_kind")
    private CriterionKind kind;

    /** {@code order} is a reserved word, so the column is a delimited identifier in the schema. */
    @Column(name = "\"order\"", nullable = false)
    private int order;

    @Column(name = "source_text", nullable = false, columnDefinition = "text")
    private String sourceText;

    @Type(JsonStringUserType.class)
    @Column(name = "normalized_rule", columnDefinition = "json")
    private String normalizedRule;

    @Column(name = "required", nullable = false)
    private boolean required = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trial_version_id", insertable = false, updatable = false)
    private TrialVersion trialVersion;

    protected Criterion() {
        // Required by JPA.
    }

    public Criterion(UUID trialVersionId, CriterionKind kind, int order, String sourceText) {
        this.trialVersionId = trialVersionId;
        this.kind = kind;
        this.order = order;
        this.sourceText = sourceText;
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

    public CriterionKind getKind() {
        return kind;
    }

    public void setKind(CriterionKind kind) {
        this.kind = kind;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public String getSourceText() {
        return sourceText;
    }

    public void setSourceText(String sourceText) {
        this.sourceText = sourceText;
    }

    public String getNormalizedRule() {
        return normalizedRule;
    }

    public void setNormalizedRule(String normalizedRule) {
        this.normalizedRule = normalizedRule;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public TrialVersion getTrialVersion() {
        return trialVersion;
    }

    public void setTrialVersion(TrialVersion trialVersion) {
        this.trialVersion = trialVersion;
        if (trialVersion != null) {
            this.trialVersionId = trialVersion.getId();
        }
    }
}
