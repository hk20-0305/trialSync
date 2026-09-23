package com.trialsync.backend.nlp;

import java.net.http.HttpClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trialsync.backend.config.TrialSyncProperties;

/**
 * Builds the configured extraction provider: {@code build_extractor(settings)}.
 *
 * <p>This is a factory rather than a bean because an extractor needs a {@link CandidateParser}, and
 * the import layer owns that parser. Keeping the wiring explicit means a deployment without an
 * import layer still starts.
 *
 * <p>The rules are Python's, unchanged:
 *
 * <table>
 *   <caption>Extraction provider selection</caption>
 *   <tr><th>{@code TRIALSYNC_EXTRACTION_PROVIDER}</th><th>API key</th><th>Result</th></tr>
 *   <tr><td>{@code disabled}</td><td>any</td><td>{@link DisabledExtractor}</td></tr>
 *   <tr><td>{@code rule_based}</td><td>any</td><td>{@link RuleBasedExtractor}</td></tr>
 *   <tr><td>{@code auto}</td><td>absent</td><td>{@link RuleBasedExtractor}</td></tr>
 *   <tr><td>{@code auto} or {@code groq}</td><td>present</td><td>{@link GroqExtractor}</td></tr>
 *   <tr><td>{@code groq}</td><td>absent</td><td>{@link RuleBasedExtractor}</td></tr>
 * </table>
 */
public class ExtractorFactory {

    private final TrialSyncProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ExtractorFactory(
            TrialSyncProperties properties, HttpClient httpClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public StructuredExtractor build(CandidateParser parser) {
        String mode = properties.getExtractionProvider();
        boolean hasKey = properties.hasGroqApiKey();
        if ("disabled".equals(mode)) {
            return new DisabledExtractor(parser);
        }
        if ("rule_based".equals(mode) || ("auto".equals(mode) && !hasKey)) {
            return new RuleBasedExtractor(parser);
        }
        if (!hasKey) {
            return new RuleBasedExtractor(parser);
        }
        return new GroqExtractor(
                NlpConfiguration.groqClient(properties, httpClient, objectMapper),
                objectMapper,
                parser);
    }

    /**
     * The deterministic extractor the import API re-runs when the configured provider fails. Kept
     * here so callers do not have to know how to construct one.
     */
    public StructuredExtractor ruleBased(CandidateParser parser) {
        return new RuleBasedExtractor(parser);
    }
}
