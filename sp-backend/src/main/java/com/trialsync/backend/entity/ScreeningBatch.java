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
import jakarta.persistence.Table;

/** A set of screenings requested together. {@code pair_count} is the requested patient/trial pairs. */
@Entity
@Table(name = "screening_batches")
public class ScreeningBatch extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "owner_id", nullable = false, columnDefinition = "uuid")
    private UUID ownerId;

    @Column(name = "label", length = 120)
    private String label;

    @Column(name = "pair_count", nullable = false)
    private int pairCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", insertable = false, updatable = false)
    private User owner;

    @OneToMany(mappedBy = "batch", fetch = FetchType.LAZY)
    @BatchSize(size = 100)
    private List<Screening> screenings = new ArrayList<>();

    protected ScreeningBatch() {
        // Required by JPA.
    }

    public ScreeningBatch(UUID ownerId, String label, int pairCount) {
        this.ownerId = ownerId;
        this.label = label;
        this.pairCount = pairCount;
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

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getPairCount() {
        return pairCount;
    }

    public void setPairCount(int pairCount) {
        this.pairCount = pairCount;
    }

    public User getOwner() {
        return owner;
    }

    public List<Screening> getScreenings() {
        return screenings;
    }
}
