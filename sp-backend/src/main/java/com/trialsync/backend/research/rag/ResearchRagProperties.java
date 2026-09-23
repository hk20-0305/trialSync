package com.trialsync.backend.research.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for research eligibility criteria RAG and Gemini integration.
 */
@Component
@ConfigurationProperties(prefix = "trialsync.research.rag")
public class ResearchRagProperties {

    private String provider = "gemini";
    private String geminiApiKey = "";
    private String geminiModel = "gemini-1.5-flash";
    private double timeoutSeconds = 15.0;
    private int maxRetries = 1;
    private int topK = 5;
    private double minScore = 0.0;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getGeminiApiKey() {
        return geminiApiKey;
    }

    public void setGeminiApiKey(String geminiApiKey) {
        this.geminiApiKey = geminiApiKey;
    }

    public boolean hasGeminiApiKey() {
        return geminiApiKey != null && !geminiApiKey.isBlank();
    }

    public String getGeminiModel() {
        return geminiModel;
    }

    public void setGeminiModel(String geminiModel) {
        this.geminiModel = geminiModel;
    }

    public double getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(double timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }
}
