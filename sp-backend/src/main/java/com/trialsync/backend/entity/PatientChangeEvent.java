package com.trialsync.backend.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.Type;

import com.trialsync.backend.entity.type.JsonStringUserType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * An append-only activity entry describing one change to a patient's mutable record.
 *
 * <p>The row has {@code created_at} but no {@code updated_at}: entries are never edited.
 * {@code before_json} and {@code after_json} hold the raw JSON documents the API echoes back, so
 * they are kept as text rather than being re-serialized on read.
 */
@Entity
@Table(name = "patient_change_events")
public class PatientChangeEvent {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "patient_id", nullable = false, columnDefinition = "uuid")
    private UUID patientId;

    @Column(name = "actor_id", nullable = false, columnDefinition = "uuid")
    private UUID actorId;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "entity_type", nullable = false, length = 32)
    private String entityType;

    @Column(name = "entity_id", columnDefinition = "uuid")
    private UUID entityId;

    @Column(name = "reason", length = 500)
    private String reason;

    @Type(JsonStringUserType.class)
    @Column(name = "before_json", columnDefinition = "json")
    private String beforeJson;

    @Type(JsonStringUserType.class)
    @Column(name = "after_json", columnDefinition = "json")
    private String afterJson;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", insertable = false, updatable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", insertable = false, updatable = false)
    private User actor;

    protected PatientChangeEvent() {
        // Required by JPA.
    }

    public PatientChangeEvent(UUID patientId, UUID actorId, String eventType, String entityType) {
        this.patientId = patientId;
        this.actorId = actorId;
        this.eventType = eventType;
        this.entityType = entityType;
    }

    @PrePersist
    void applyInsertTimestamp() {
        if (createdAt == null) {
            createdAt = TimestampedEntity.nowUtc();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public void setPatientId(UUID patientId) {
        this.patientId = patientId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getBeforeJson() {
        return beforeJson;
    }

    public void setBeforeJson(String beforeJson) {
        this.beforeJson = beforeJson;
    }

    public String getAfterJson() {
        return afterJson;
    }

    public void setAfterJson(String afterJson) {
        this.afterJson = afterJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Patient getPatient() {
        return patient;
    }

    public User getActor() {
        return actor;
    }
}
