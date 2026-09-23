package com.trialsync.backend.nlp;

import java.util.List;

/**
 * The provider installed when the explanation assistant is switched off, or when {@code groq} was
 * requested without an API key.
 *
 * <p>Port of {@code DisabledScreeningChatProvider}. It refuses to answer rather than quietly
 * falling back to the deterministic explainer, so an operator who disabled the assistant sees an
 * explicit {@code ASSISTANT_DISABLED} instead of an answer they did not expect to be offered.
 */
public class DisabledScreeningChatProvider implements ScreeningChatProvider {

    @Override
    public String providerName() {
        return "disabled";
    }

    @Override
    public String modelId() {
        return null;
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public ChatAnswer answer(
            ScreeningChatContext context, List<ChatMessage> history, String message) {
        throw new ProviderCallException(
                "ASSISTANT_DISABLED", "The explanation assistant is disabled.");
    }
}
