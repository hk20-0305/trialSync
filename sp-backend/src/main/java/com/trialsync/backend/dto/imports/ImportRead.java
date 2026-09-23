package com.trialsync.backend.dto.imports;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.trialsync.backend.entity.enums.DocumentKind;
import com.trialsync.backend.entity.enums.DocumentSourceType;

/**
 * Port of {@code trialsync.imports.schemas.ImportRead}, the review payload the whole import UI is
 * built on. Component order reproduces the Pydantic field order.
 *
 * <p>{@code pages}, {@code candidates} and {@code quality} are open JSON on both sides and are
 * carried through as parsed trees, so key order and every value the extractor recorded survive the
 * round trip unchanged.
 */
public record ImportRead(
        @JsonProperty("id") UUID id,
        @JsonProperty("kind") DocumentKind kind,
        @JsonProperty("source_type") DocumentSourceType sourceType,
        @JsonProperty("status") String status,
        @JsonProperty("filename") String filename,
        @JsonProperty("mime_type") String mimeType,
        @JsonProperty("size_bytes") int sizeBytes,
        @JsonProperty("checksum") String checksum,
        @JsonProperty("source_text") String sourceText,
        @JsonProperty("pages") JsonNode pages,
        @JsonProperty("candidates") JsonNode candidates,
        @JsonProperty("warnings") List<String> warnings,
        @JsonProperty("quality") JsonNode quality,
        @JsonProperty("approved_resource_id") UUID approvedResourceId,
        @JsonProperty("created_at") OffsetDateTime createdAt) {}
