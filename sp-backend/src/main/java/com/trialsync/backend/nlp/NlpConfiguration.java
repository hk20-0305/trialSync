package com.trialsync.backend.nlp;

import java.net.http.HttpClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trialsync.backend.config.TrialSyncProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the extraction and explanation providers from configuration, porting
 * {@code build_chat_provider} and {@code build_extractor}.
 *
 * <p>The selection rules are reproduced exactly, and in particular the two places where a missing
 * credential decides the outcome:
 *
 * <ul>
 *   <li>chat, with no {@code TRIALSYNC_GROQ_API_KEY}: {@code auto} yields the deterministic
 *       explainer, while an explicit {@code groq} yields the <em>disabled</em> provider - asking for
 *       a provider that cannot run is an error the operator should see, not something to paper over;
 *   <li>extraction, with no key: both {@code auto} and an explicit {@code groq} fall back to the
 *       rule-based parser, because an import must still produce candidates.
 * </ul>
 *
 * <p>No key is ever invented and no provider is faked. Without a credential the service does exactly
 * what the Python service did without one.
 */
@Configuration
public class NlpConfiguration {

    /**
     * {@code build_chat_provider(settings)}.
     *
     * <p>The provider is wrapped in a mutable holder rather than injected directly, mirroring
     * {@code app.state.chat_provider}.
     */
    @Bean
    public ScreeningChatProviderHolder screeningChatProviderHolder(
            TrialSyncProperties properties, HttpClient externalHttpClient, ObjectMapper objectMapper) {
        return new ScreeningChatProviderHolder(
                buildChatProvider(properties, externalHttpClient, objectMapper));
    }

    /** The factory the import layer calls once it can supply a deterministic candidate parser. */
    @Bean
    public ExtractorFactory extractorFactory(
            TrialSyncProperties properties, HttpClient externalHttpClient, ObjectMapper objectMapper) {
        return new ExtractorFactory(properties, externalHttpClient, objectMapper);
    }

    static ScreeningChatProvider buildChatProvider(
            TrialSyncProperties properties, HttpClient httpClient, ObjectMapper objectMapper) {
        String mode = properties.getScreeningChatProvider();
        boolean hasKey = properties.hasGroqApiKey();
        if ("disabled".equals(mode)) {
            return new DisabledScreeningChatProvider();
        }
        if ("canonical".equals(mode) || ("auto".equals(mode) && !hasKey)) {
            return new CanonicalExplainer();
        }
        if (!hasKey) {
            return new DisabledScreeningChatProvider();
        }
        return new GroqScreeningChatProvider(
                groqClient(properties, httpClient, objectMapper),
                objectMapper,
                properties.getScreeningChatMaxAnswerChars());
    }

    static GroqStructuredClient groqClient(
            TrialSyncProperties properties, HttpClient httpClient, ObjectMapper objectMapper) {
        return new GroqStructuredClient(
                httpClient,
                objectMapper,
                properties.getGroqApiKey(),
                properties.getGroqModel(),
                properties.getProviderTimeoutSeconds(),
                properties.getProviderMaxRetries());
    }
}
