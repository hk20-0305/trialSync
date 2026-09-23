package com.trialsync.backend.nlp;

import com.trialsync.backend.entity.enums.DocumentKind;
import com.trialsync.backend.imports.ExtractedInput;

/**
 * The deterministic extractor: {@code trialsync.nlp.extraction.RuleBasedExtractor}.
 *
 * <p>It delegates straight to the regular-expression import parser, so it needs no credential, no
 * network and no model. This is the default whenever Groq is not configured, and the safety net the
 * import API falls back to whenever Groq fails.
 */
public class RuleBasedExtractor implements StructuredExtractor {

    private final CandidateParser parser;

    public RuleBasedExtractor(CandidateParser parser) {
        this.parser = parser;
    }

    @Override
    public String providerName() {
        return "rule_based";
    }

    @Override
    public ExtractionRun extract(DocumentKind kind, ExtractedInput extracted) {
        long started = Latency.start();
        CandidateExtraction extraction =
                kind == DocumentKind.patient
                        ? parser.extractPatientCandidates(extracted)
                        : parser.extractTrialCandidates(extracted);
        return new ExtractionRun(
                extraction.candidates(),
                extraction.warnings(),
                Extractions.metadata(
                        "rule_based", Extractions.DETERMINISTIC_MODEL_ID, started, "valid"));
    }
}
