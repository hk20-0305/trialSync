package com.trialsync.backend.dto.imports;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Port of {@code trialsync.imports.schemas.SourceReference}: where a candidate was found.
 *
 * <p>{@code start} and {@code end} are page-local code point offsets into the page text, not offsets
 * into the whole document. {@code span_id} is null in the payload the model produces and is filled
 * in once the corresponding {@code document_spans} row exists; on every later round trip the server
 * rewrites all five fields from that row, so a client cannot move a candidate's provenance.
 */
public record SourceReference(
        @JsonProperty("span_id") UUID spanId,
        @JsonProperty("page") int page,
        @JsonProperty("start") int start,
        @JsonProperty("end") int end,
        @JsonProperty("text") String text) {}
