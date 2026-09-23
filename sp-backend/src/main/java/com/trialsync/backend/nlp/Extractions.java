package com.trialsync.backend.nlp;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared constants and the provenance-metadata builder for the extraction providers.
 *
 * <p>Port of {@code EXTRACTION_PROMPT_VERSION} and {@code _metadata} from
 * {@code trialsync.nlp.extraction}.
 */
public final class Extractions {

    /** Prompt revision stamped on every extraction, deterministic ones included. */
    public static final String EXTRACTION_PROMPT_VERSION = "reviewed-extraction-v1";

    /** Model identifier recorded for the deterministic parser. */
    public static final String DETERMINISTIC_MODEL_ID = "deterministic-parser-1";

    private Extractions() {}

    /**
     * {@code _metadata}: the provenance block stored alongside the candidates, in the Python key
     * order because it is serialised into {@code documents.quality_json}.
     */
    public static Map<String, Object> metadata(
            String provider, String modelId, long startNanos, String outcome) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", provider);
        metadata.put("model_id", modelId);
        metadata.put("prompt_version", EXTRACTION_PROMPT_VERSION);
        metadata.put("latency_ms", Latency.millisSince(startNanos));
        metadata.put("validation_outcome", outcome);
        return metadata;
    }
}
