package com.trialsync.backend.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLOrder;
import org.hibernate.type.SqlTypes;

import com.trialsync.backend.entity.enums.VersionStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * One revision of a trial's eligibility criteria.
 *
 * <p>A draft is editable; once approved it is frozen, because saved screenings cite it and the
 * {@code screenings_trial_version_id_fkey} foreign key is {@code ON DELETE RESTRICT}.
 */
@Entity
@Table(name = "trial_versions")
public class TrialVersion extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "trial_id", nullable = false, columnDefinition = "uuid")
    private UUID trialId;

    @Column(name = "version", nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "version_status")
    private VersionStatus status = VersionStatus.draft;

    @Column(name = "source_text", columnDefinition = "text")
    private String sourceText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trial_id", insertable = false, updatable = false)
    private Trial trial;

    @OneToMany(mappedBy = "trialVersion", fetch = FetchType.LAZY)
    @jakarta.persistence.OrderBy("order ASC")
    @BatchSize(size = 100)
    private List<Criterion> criteria = new ArrayList<>();

    protected TrialVersion() {
        // Required by JPA.
    }

    public TrialVersion(UUID trialId, int version) {
        this.trialId = trialId;
        this.version = version;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTrialId() {
        return trialId;
    }

    public void setTrialId(UUID trialId) {
        this.trialId = trialId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public VersionStatus getStatus() {
        return status;
    }

    public void setStatus(VersionStatus status) {
        this.status = status;
    }

    public String getSourceText() {
        return sourceText;
    }

    public void setSourceText(String sourceText) {
        this.sourceText = sourceText;
    }

    public Trial getTrial() {
        return trial;
    }

    public void setTrial(Trial trial) {
        this.trial = trial;
        if (trial != null) {
            this.trialId = trial.getId();
        }
    }

    public List<Criterion> getCriteria() {
        return criteria;
    }
}
