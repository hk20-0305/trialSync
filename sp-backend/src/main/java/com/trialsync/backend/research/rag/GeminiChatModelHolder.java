package com.trialsync.backend.research.rag;

import dev.langchain4j.model.chat.ChatLanguageModel;

/**
 * Mutable holder for the Gemini ChatLanguageModel instance, allowing test suites to inject
 * mocks or custom implementations without restarting the Spring context.
 */
public class GeminiChatModelHolder {

    private volatile ChatLanguageModel model;
    private volatile boolean available;

    public GeminiChatModelHolder(ChatLanguageModel model, boolean available) {
        this.model = model;
        this.available = available && model != null;
    }

    public boolean isAvailable() {
        return available && model != null;
    }

    public ChatLanguageModel getModel() {
        return model;
    }

    public void setModel(ChatLanguageModel model) {
        this.model = model;
        this.available = (model != null);
    }

    public void setUnavailable() {
        this.available = false;
        this.model = null;
    }
}
