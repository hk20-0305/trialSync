package com.trialsync.backend.nlp;

import com.trialsync.backend.entity.enums.DocumentKind;
import com.trialsync.backend.imports.ExtractedInput;

/**
 * Port of the {@code StructuredExtractor} protocol.
 *
 * <p>An extractor turns an uploaded document into <em>review candidates</em>. It never writes a
 * record, never evaluates a criterion and never produces an eligibility verdict; its entire output
 * is a proposal that a human confirms.
 *
 * <p>Implementations raise {@link ProviderCallException} when they cannot produce candidates. The
 * import API turns that into a deterministic re-run plus a visible warning rather than an error,
 * so an unavailable provider degrades to the rule-based parser instead of blocking the import.
 */
public interface StructuredExtractor {

    /** Stable identifier recorded in {@code metadata.provider}. */
    String providerName();

    ExtractionRun extract(DocumentKind kind, ExtractedInput extracted);
}
