package com.trialsync.backend.imports;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One page of extracted text and its position inside the concatenated document text.
 *
 * <p>Serialised into {@code documents.pages_json} and returned as {@code pages} on the import read
 * model. The offsets are code point indexes into {@link ExtractedInput#text()}, matching Python's
 * {@code str} indexing rather than Java's UTF-16 indexing.
 */
public record ExtractedPage(
        @JsonProperty("page") int page,
        @JsonProperty("start_offset") int startOffset,
        @JsonProperty("end_offset") int endOffset,
        @JsonProperty("text") String text) {}
