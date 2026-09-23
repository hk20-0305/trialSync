package com.trialsync.backend.dto.imports;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.trialsync.backend.entity.enums.DocumentKind;

/**
 * Port of {@code trialsync.imports.schemas.ImportApprovalRead}: what approval produced.
 *
 * <p>{@code resource_id} is the new patient or trial, and {@code review_id} the document that was
 * approved, so the client can navigate to either.
 */
public record ImportApprovalRead(
        @JsonProperty("kind") DocumentKind kind,
        @JsonProperty("resource_id") UUID resourceId,
        @JsonProperty("review_id") UUID reviewId) {}
