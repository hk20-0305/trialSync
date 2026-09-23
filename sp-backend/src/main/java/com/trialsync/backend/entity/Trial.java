package com.trialsync.backend.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** A protocol. Its eligibility criteria live on versions, not on the trial itself. */
@Entity
@Table(name = "trials")
public class Trial extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "owner_id", nullable = false, columnDefinition = "uuid")
    private UUID ownerId;

    @Column(name = "registry_id", nullable = false, length = 64)
    private String registryId;

    @Column(name = "title", nullable = false, length = 240)
    private String title;

    @Column(name = "condition", nullable = false, length = 160)
    private String condition;

    @Column(name = "phase", length = 40)
    private String phase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", insertable = false, updatable = false)
    private User owner;

    @OneToMany(mappedBy = "trial", fetch = FetchType.LAZY)
    @OrderBy("version")
    @BatchSize(size = 100)
    private List<TrialVersion> versions = new ArrayList<>();

    protected Trial() {
        // Required by JPA.
    }

    public Trial(UUID ownerId, String registryId, String title, String condition) {
        this.ownerId = ownerId;
        this.registryId = registryId;
        this.title = title;
        this.condition = condition;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public String getRegistryId() {
        return registryId;
    }

    public void setRegistryId(String registryId) {
        this.registryId = registryId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public User getOwner() {
        return owner;
    }

    public List<TrialVersion> getVersions() {
        return versions;
    }
}
