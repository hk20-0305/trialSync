package com.trialsync.backend.nlp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.trialsync.backend.entity.enums.DocumentKind;
import com.trialsync.backend.imports.ExtractedInput;

/**
 * The extractor installed when external NLP is switched off:
 * {@code trialsync.nlp.extraction.DisabledExtractor}.
 *
 * <p>It runs the deterministic parser and then says so. The import still succeeds and still produces
 * candidates - an operator who disabled the external provider gets a working import, not a failure -
 * but the reviewer sees an explicit warning and the stored metadata records that {@code disabled}
 * was the configured provider.
 *
 * <p>The metadata keeps {@code provider: "rule_based"} from the parent run, exactly as Python did;
 * the configured mode is carried separately in {@code requested_provider}.
 */
public class DisabledExtractor extends RuleBasedExtractor {

    /** The warning prepended to whatever the deterministic parse reported. */
    public static final String DISABLED_WARNING =
            "External NLP is disabled; deterministic extraction was used.";

    public DisabledExtractor(CandidateParser parser) {
        super(parser);
    }

    /**
     * Reports the configured mode rather than {@code rule_based}, so the import layer can tell an
     * intentionally disabled provider from one that simply has no key.
     */
    @Override
    public String providerName() {
        return "disabled";
    }

    @Override
    public ExtractionRun extract(DocumentKind kind, ExtractedInput extracted) {
        ExtractionRun run = super.extract(kind, extracted);
        List<String> warnings = new ArrayList<>();
        warnings.add(DISABLED_WARNING);
        warnings.addAll(run.warnings());
        Map<String, Object> metadata = new LinkedHashMap<>(run.metadata());
        metadata.put("requested_provider", "disabled");
        return new ExtractionRun(run.candidates(), warnings, metadata);
    }
}
