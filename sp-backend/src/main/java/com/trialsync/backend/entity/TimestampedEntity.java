package com.trialsync.backend.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * The {@code created_at} / {@code updated_at} pair shared by most tables.
 *
 * <p>Both columns are {@code timestamptz} with a {@code now()} server default, and the Python
 * mapping additionally declares {@code onupdate=func.now()} so every UPDATE refreshes
 * {@code updated_at}. The callbacks below reproduce that: the values are only defaulted when the
 * caller did not supply them, and {@code updated_at} is refreshed on every flush that actually
 * dirties the row.
 *
 * <p>The timestamps are generated in UTC and truncated to microseconds so the in-memory value is
 * byte-identical to what PostgreSQL stores and returns. That matters because {@code updated_at} is
 * handed to clients and sent back as {@code expected_updated_at} for optimistic locking, and
 * because responses are serialized directly from these entities.
 */
@MappedSuperclass
public abstract class TimestampedEntity {

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime updatedAt;

    /** The clock used for every generated timestamp, matching PostgreSQL's microsecond precision. */
    public static OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    @PrePersist
    void applyInsertTimestamps() {
        OffsetDateTime now = nowUtc();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void applyUpdateTimestamp() {
        updatedAt = nowUtc();
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
