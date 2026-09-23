package com.trialsync.backend.research.rag.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload for an approved trial version indexing operation.
 */
public record TrialCriteriaIngestResponse(
        @JsonProperty("trial_version_id") UUID trialVersionId,
        @JsonProperty("status") String status,
        @JsonProperty("chunk_count") int chunkCount,
        @JsonProperty("corpus_checksum") String corpusChecksum,
        @JsonProperty("indexed_at") OffsetDateTime indexedAt,
        @JsonProperty("message") String message
) {}
