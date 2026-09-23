package com.trialsync.backend.nlp;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Port of {@code trialsync.nlp.groq.GroqStructuredClient}: a minimal OpenAI-compatible client with
 * no tool calling, no streaming and no payload logging.
 *
 * <p>The wire contract is preserved exactly - the same endpoint, {@code temperature: 0}, the
 * {@code max_completion_tokens} field name (not {@code max_tokens}), and a strict
 * {@code response_format.json_schema} envelope so the provider is constrained to the schema the
 * caller supplies.
 *
 * <p>Retry behaviour, copied statement for statement:
 *
 * <ul>
 *   <li>up to {@code maxRetries} extra attempts after the first;
 *   <li>a transport timeout or transport error retries <em>immediately</em>, with no sleep;
 *   <li>{@code 429} and {@code 5xx} sleep for the {@code retry-after} header clamped to
 *       {@code [0.0, 1.0]} seconds, defaulting to {@code 0.1} when the header is absent or
 *       unparseable;
 *   <li>a {@code 5xx} on the final attempt falls through to the generic {@code >= 400} branch and
 *       becomes {@code PROVIDER_ERROR}, not {@code PROVIDER_TIMEOUT}.
 * </ul>
 *
 * <p>Nothing this class returns may influence an eligibility verdict. Callers use it to produce
 * review candidates a human then approves, or prose explaining evidence that is already stored.
 */
public class GroqStructuredClient {

    /** The OpenAI-compatible Groq endpoint. Unchanged from the Python client. */
    public static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";

    private static final double DEFAULT_RETRY_SECONDS = 0.1;
    private static final double MAX_RETRY_SECONDS = 1.0;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final double timeoutSeconds;
    private final int maxRetries;

    public GroqStructuredClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String apiKey,
            String model,
            double timeoutSeconds,
            int maxRetries) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.maxRetries = maxRetries;
    }

    public String getModel() {
        return model;
    }

    /**
     * Requests one schema-constrained completion.
     *
     * @throws ProviderCallException with a {@code PROVIDER_*} code for every failure mode; the
     *     caller never sees a transport exception or a provider body.
     */
    public GroqCompletion complete(
            List<ChatMessage> messages, String schemaName, JsonNode schema, int maxTokens) {
        HttpRequest request = buildRequest(messages, schemaName, schema, maxTokens);

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            HttpResponse<String> response;
            try {
                response =
                        httpClient.send(
                                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (HttpTimeoutException exception) {
                // Python retried a timeout without sleeping; only the rate-limit and 5xx branches
                // back off.
                if (attempt < maxRetries) {
                    continue;
                }
                throw new ProviderCallException("PROVIDER_TIMEOUT", "The provider timed out.");
            } catch (IOException exception) {
                if (attempt < maxRetries) {
                    continue;
                }
                throw new ProviderCallException(
                        "PROVIDER_ERROR", "The provider could not be reached.");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ProviderCallException(
                        "PROVIDER_ERROR", "The provider could not be reached.");
            }

            int status = response.statusCode();
            if (status == 429) {
                if (attempt < maxRetries) {
                    sleep(retryDelaySeconds(response));
                    continue;
                }
                throw new ProviderCallException(
                        "PROVIDER_RATE_LIMITED", "The provider rate limit was reached.");
            }
            if (status >= 500 && attempt < maxRetries) {
                sleep(retryDelaySeconds(response));
                continue;
            }
            if (status >= 400) {
                throw new ProviderCallException(
                        "PROVIDER_ERROR", "The provider rejected the request.");
            }
            return parse(response.body());
        }
        // Unreachable: the final attempt always returns or raises. Kept because the Python client
        // kept the same guard after its loop.
        throw new ProviderCallException("PROVIDER_ERROR", "The provider request did not complete.");
    }

    private HttpRequest buildRequest(
            List<ChatMessage> messages, String schemaName, JsonNode schema, int maxTokens) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ArrayNode messageArray = body.putArray("messages");
        for (ChatMessage message : messages) {
            ObjectNode node = messageArray.addObject();
            node.put("role", message.role());
            node.put("content", message.content());
        }
        // An integer literal, exactly as the Python dict carried it: `0`, never `0.0`.
        body.put("temperature", 0);
        body.put("max_completion_tokens", maxTokens);
        ObjectNode responseFormat = body.putObject("response_format");
        responseFormat.put("type", "json_schema");
        ObjectNode jsonSchema = responseFormat.putObject("json_schema");
        jsonSchema.put("name", schemaName);
        jsonSchema.put("strict", true);
        jsonSchema.set("schema", schema == null ? objectMapper.createObjectNode() : schema);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new ProviderCallException(
                    "PROVIDER_ERROR", "The provider could not be reached.");
        }

        return HttpRequest.newBuilder(URI.create(ENDPOINT))
                .timeout(Duration.ofMillis(Math.max(1L, Math.round(timeoutSeconds * 1_000))))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
    }

    /**
     * Extracts {@code choices[0].message.content}, parses it as a JSON object and reads the usage
     * counters. Every structural surprise collapses to one opaque code, as in Python, so a
     * misbehaving provider cannot leak its response shape to a client.
     */
    private GroqCompletion parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new IllegalStateException("choices missing");
            }
            JsonNode content = choices.get(0).path("message").path("content");
            if (!content.isTextual()) {
                throw new IllegalStateException("content missing");
            }
            JsonNode payload = objectMapper.readTree(content.asText());
            if (!payload.isObject()) {
                throw new IllegalStateException("payload is not an object");
            }
            JsonNode usage = root.path("usage");
            return new GroqCompletion(
                    (ObjectNode) payload,
                    optionalInt(usage.path("prompt_tokens")),
                    optionalInt(usage.path("completion_tokens")));
        } catch (RuntimeException | IOException exception) {
            throw new ProviderCallException(
                    "PROVIDER_RESPONSE_INVALID", "The provider returned an invalid response.");
        }
    }

    /** {@code _optional_int}: only a genuine JSON integer counts; floats and strings become null. */
    private static Integer optionalInt(JsonNode node) {
        return node != null && node.isIntegralNumber() ? node.intValue() : null;
    }

    /** {@code _retry_delay}: the {@code retry-after} header clamped into {@code [0.0, 1.0]}. */
    private static double retryDelaySeconds(HttpResponse<String> response) {
        String value = response.headers().firstValue("retry-after").orElse("0.1");
        try {
            return Math.min(Math.max(Double.parseDouble(value.trim()), 0.0), MAX_RETRY_SECONDS);
        } catch (NumberFormatException exception) {
            return DEFAULT_RETRY_SECONDS;
        }
    }

    private static void sleep(double seconds) {
        long millis = Math.round(seconds * 1_000);
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderCallException("PROVIDER_ERROR", "The provider could not be reached.");
        }
    }
}
