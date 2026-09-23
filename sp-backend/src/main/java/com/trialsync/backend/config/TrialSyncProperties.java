package com.trialsync.backend.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mirrors the Python {@code trialsync.config.Settings} contract field for field so the migrated
 * service honours the same environment variables and the same validation bounds.
 *
 * <p>Bounds are enforced in {@link #validate()} rather than with bean-validation annotations so a
 * misconfigured value fails fast at startup with the same message the Python service produced.
 */
@ConfigurationProperties(prefix = "trialsync")
public class TrialSyncProperties {

    public static final String ENV_DEVELOPMENT = "development";
    public static final String ENV_TEST = "test";
    public static final String ENV_PRODUCTION = "production";

    private String appName = "TrialSync API";
    private String environment = ENV_DEVELOPMENT;
    private boolean debug = false;
    private String authSecret = "";
    private int accessTokenMinutes = 480;
    private List<String> corsOrigins = new ArrayList<>();
    private int screeningBatchMaxPatients = 50;
    private int screeningBatchMaxTrials = 10;
    private int screeningBatchMaxPairs = 500;
    private String groqApiKey = "";
    private String groqModel = "openai/gpt-oss-20b";
    private String extractionProvider = "groq";
    private String screeningChatProvider = "auto";
    private double providerTimeoutSeconds = 12.0;
    private int providerMaxRetries = 1;
    private int providerMaxInputChars = 100_000;
    private int screeningChatMessageMaxChars = 1_000;
    private int screeningChatMaxMessages = 10;
    private int screeningChatMaxAnswerChars = 2_000;
    private boolean terminologySuggestionsEnabled = true;
    private double terminologyTimeoutSeconds = 5.0;
    private int terminologyMaxResults = 5;
    private String loincUsername = "";
    private String loincPassword = "";

    /** Validates every bound the Python settings model declared. */
    public void validate() {
        requireChoice("environment", environment, ENV_DEVELOPMENT, ENV_TEST, ENV_PRODUCTION);
        requireRange("access_token_minutes", accessTokenMinutes, 5, 1440);
        requireRange("screening_batch_max_patients", screeningBatchMaxPatients, 1, 100);
        requireRange("screening_batch_max_trials", screeningBatchMaxTrials, 1, 50);
        requireRange("screening_batch_max_pairs", screeningBatchMaxPairs, 1, 1000);
        requireText("groq_model", groqModel, 1, 120);
        requireChoice("extraction_provider", extractionProvider, "auto", "rule_based", "groq", "disabled");
        requireChoice("screening_chat_provider", screeningChatProvider, "auto", "canonical", "groq", "disabled");
        requireRange("provider_timeout_seconds", providerTimeoutSeconds, 1.0, 30.0);
        requireRange("provider_max_retries", providerMaxRetries, 0, 2);
        requireRange("provider_max_input_chars", providerMaxInputChars, 1_000, 200_000);
        requireRange("screening_chat_message_max_chars", screeningChatMessageMaxChars, 100, 4_000);
        requireRange("screening_chat_max_messages", screeningChatMaxMessages, 2, 20);
        if (screeningChatMaxMessages % 2 != 0) {
            throw new IllegalStateException(
                    "TRIALSYNC_SCREENING_CHAT_MAX_MESSAGES must be a multiple of 2");
        }
        requireRange("screening_chat_max_answer_chars", screeningChatMaxAnswerChars, 200, 4_000);
        requireRange("terminology_timeout_seconds", terminologyTimeoutSeconds, 1.0, 10.0);
        requireRange("terminology_max_results", terminologyMaxResults, 1, 10);
    }

    /**
     * Returns the signing secret, refusing anything shorter than the Python minimum. Mirrors
     * {@code Settings.require_auth_secret}.
     */
    public String requireAuthSecret() {
        if (authSecret == null || authSecret.length() < 32) {
            throw new IllegalStateException(
                    "TRIALSYNC_AUTH_SECRET must contain at least 32 characters");
        }
        return authSecret;
    }

    public boolean hasGroqApiKey() {
        return groqApiKey != null && !groqApiKey.isBlank();
    }

    public boolean hasLoincCredentials() {
        return loincUsername != null
                && !loincUsername.isBlank()
                && loincPassword != null
                && !loincPassword.isBlank();
    }

    private static void requireChoice(String name, String value, String... allowed) {
        for (String option : allowed) {
            if (option.equals(value)) {
                return;
            }
        }
        throw new IllegalStateException(
                "trialsync." + name + " must be one of " + String.join(", ", allowed));
    }

    private static void requireRange(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalStateException(
                    "trialsync." + name + " must be between " + min + " and " + max);
        }
    }

    private static void requireRange(String name, double value, double min, double max) {
        if (value < min || value > max) {
            throw new IllegalStateException(
                    "trialsync." + name + " must be between " + min + " and " + max);
        }
    }

    private static void requireText(String name, String value, int minLen, int maxLen) {
        int len = (value == null) ? 0 : value.length();
        if (len < minLen || len > maxLen) {
            throw new IllegalStateException(
                    "trialsync." + name + " must be between " + minLen + " and " + maxLen + " characters");
        }
    }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public boolean isDebug() { return debug; }
    public void setDebug(boolean debug) { this.debug = debug; }

    public String getAuthSecret() { return authSecret; }
    public void setAuthSecret(String authSecret) { this.authSecret = authSecret; }

    public int getAccessTokenMinutes() { return accessTokenMinutes; }
    public void setAccessTokenMinutes(int accessTokenMinutes) {
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public List<String> getCorsOrigins() { return corsOrigins; }
    public void setCorsOrigins(List<String> corsOrigins) {
        if (corsOrigins == null) {
            this.corsOrigins = new ArrayList<>();
            return;
        }
        List<String> cleaned = new ArrayList<>();
        for (String origin : corsOrigins) {
            if (origin == null) continue;
            String s = origin.trim().replaceAll("^\\[|\\]$", "").replaceAll("^\"|\"$", "").replaceAll("^'|'$", "").trim();
            if (!s.isEmpty()) {
                cleaned.add(s);
            }
        }
        this.corsOrigins = cleaned;
    }

    public int getScreeningBatchMaxPatients() { return screeningBatchMaxPatients; }
    public void setScreeningBatchMaxPatients(int value) { this.screeningBatchMaxPatients = value; }

    public int getScreeningBatchMaxTrials() { return screeningBatchMaxTrials; }
    public void setScreeningBatchMaxTrials(int value) { this.screeningBatchMaxTrials = value; }

    public int getScreeningBatchMaxPairs() { return screeningBatchMaxPairs; }
    public void setScreeningBatchMaxPairs(int value) { this.screeningBatchMaxPairs = value; }

    public String getGroqApiKey() { return groqApiKey; }
    public void setGroqApiKey(String groqApiKey) { this.groqApiKey = groqApiKey; }

    public String getGroqModel() { return groqModel; }
    public void setGroqModel(String groqModel) { this.groqModel = groqModel; }

    public String getExtractionProvider() { return extractionProvider; }
    public void setExtractionProvider(String v) { this.extractionProvider = v; }

    public String getScreeningChatProvider() { return screeningChatProvider; }
    public void setScreeningChatProvider(String v) { this.screeningChatProvider = v; }

    public double getProviderTimeoutSeconds() { return providerTimeoutSeconds; }
    public void setProviderTimeoutSeconds(double v) { this.providerTimeoutSeconds = v; }

    public int getProviderMaxRetries() { return providerMaxRetries; }
    public void setProviderMaxRetries(int v) { this.providerMaxRetries = v; }

    public int getProviderMaxInputChars() { return providerMaxInputChars; }
    public void setProviderMaxInputChars(int v) { this.providerMaxInputChars = v; }

    public int getScreeningChatMessageMaxChars() { return screeningChatMessageMaxChars; }
    public void setScreeningChatMessageMaxChars(int v) { this.screeningChatMessageMaxChars = v; }

    public int getScreeningChatMaxMessages() { return screeningChatMaxMessages; }
    public void setScreeningChatMaxMessages(int v) { this.screeningChatMaxMessages = v; }

    public int getScreeningChatMaxAnswerChars() { return screeningChatMaxAnswerChars; }
    public void setScreeningChatMaxAnswerChars(int v) { this.screeningChatMaxAnswerChars = v; }

    public boolean isTerminologySuggestionsEnabled() { return terminologySuggestionsEnabled; }
    public void setTerminologySuggestionsEnabled(boolean v) { this.terminologySuggestionsEnabled = v; }

    public double getTerminologyTimeoutSeconds() { return terminologyTimeoutSeconds; }
    public void setTerminologyTimeoutSeconds(double v) { this.terminologyTimeoutSeconds = v; }

    public int getTerminologyMaxResults() { return terminologyMaxResults; }
    public void setTerminologyMaxResults(int v) { this.terminologyMaxResults = v; }

    public String getLoincUsername() { return loincUsername; }
    public void setLoincUsername(String loincUsername) { this.loincUsername = loincUsername; }

    public String getLoincPassword() { return loincPassword; }
    public void setLoincPassword(String loincPassword) { this.loincPassword = loincPassword; }
}
