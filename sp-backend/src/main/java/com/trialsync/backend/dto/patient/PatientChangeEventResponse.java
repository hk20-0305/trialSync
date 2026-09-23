package com.trialsync.backend.dto.patient;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * One immutable entry from the patient activity trail.
 *
 * <p>{@code before_json} and {@code after_json} are echoed as JSON objects, not as strings: the
 * entity stores the raw document text so PostgreSQL's {@code json} column preserves key order, and
 * the service parses it back into a tree on the way out.
 */
@JsonPropertyOrder({
    "id",
    "patient_id",
    "actor_id",
    "event_type",
    "entity_type",
    "entity_id",
    "reason",
    "before_json",
    "after_json",
    "created_at"
})
public record PatientChangeEventResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("patient_id") UUID patientId,
        @JsonProperty("actor_id") UUID actorId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("entity_type") String entityType,
        @JsonProperty("entity_id") UUID entityId,
        @JsonProperty("reason") String reason,
        @JsonProperty("before_json") JsonNode beforeJson,
        @JsonProperty("after_json") JsonNode afterJson,
        @JsonProperty("created_at") OffsetDateTime createdAt) {}
