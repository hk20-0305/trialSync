package com.trialsync.backend.imports;

import java.util.List;
import java.util.Map;

/**
 * Port of {@code trialsync.imports.parser.ExtractedInput}: the normalised source text, its page
 * map, and the quality metrics recorded alongside it.
 *
 * <p>{@code quality} stays a mutable insertion-ordered map because the PDF path adds an
 * {@code "ocr"} entry after the metrics have been computed, and the key order is part of the stored
 * JSON.
 */
public record ExtractedInput(String text, List<ExtractedPage> pages, Map<String, Object> quality) {}
