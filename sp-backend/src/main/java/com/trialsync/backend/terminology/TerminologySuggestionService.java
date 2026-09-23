package com.trialsync.backend.terminology;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trialsync.backend.config.TrialSyncProperties;
import com.trialsync.backend.domain.model.FactType;
import com.trialsync.backend.dto.concept.TerminologySuggestion;
import org.springframework.stereotype.Service;

/**
 * Port of {@code trialsync.terminology.suggestions.TerminologySuggestionService}.
 *
 * <p>Looks up candidate codes for a clinical concept in RxNorm (medications, via the public RxNav
 * approximate-match API) and LOINC (observations, via the credentialed Regenstrief search API).
 *
 * <p>This layer normalises <em>concepts</em> and nothing else. It is never consulted during
 * screening, it never sees a patient fact or a criterion rule, and the deterministic engine has no
 * dependency on it - which is what allows an external terminology service to be slow, unauthorised
 * or entirely down without any effect on an eligibility result.
 *
 * <p>Every failure is reported as an unavailable source rather than an error: no credential means a
 * message naming the two environment variables that are missing, and a transport or HTTP failure
 * means a "could not be reached" note. Nothing is guessed and no result is synthesised when a source
 * cannot answer.
 */
@Service
public class TerminologySuggestionService {

    /** RxNav approximate-term endpoint. Public, no credential. */
    public static final String RXNAV_APPROXIMATE_URL =
            "https://rxnav.nlm.nih.gov/REST/approximateTerm.json";

    /** Regenstrief LOINC search endpoint. Requires a LOINC account. */
    public static final String LOINC_SEARCH_URL = "https://loinc.regenstrief.org/searchapi/loincs";

    static final String DISABLED_MESSAGE = "Terminology suggestions are disabled.";
    static final String UNSUPPORTED_MESSAGE =
            "External suggestions are available for medications and observations only.";
    static final String RXNORM_UNREACHABLE_MESSAGE = "RxNorm could not be reached. Try again later.";
    static final String LOINC_MISSING_CREDENTIALS_MESSAGE =
            "LOINC search needs TRIALSYNC_LOINC_USERNAME and TRIALSYNC_LOINC_PASSWORD.";
    static final String LOINC_UNREACHABLE_MESSAGE = "LOINC could not be reached. Try again later.";

    private static final List<String> LOINC_ROW_KEYS = List.of("Results", "results", "items", "data");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final double timeoutSeconds;
    private final int maxResults;
    private final String loincUsername;
    private final String loincPassword;

    public TerminologySuggestionService(
            TrialSyncProperties properties, HttpClient externalHttpClient, ObjectMapper objectMapper) {
        this.httpClient = externalHttpClient;
        this.objectMapper = objectMapper;
        this.enabled = properties.isTerminologySuggestionsEnabled();
        this.timeoutSeconds = properties.getTerminologyTimeoutSeconds();
        this.maxResults = properties.getTerminologyMaxResults();
        this.loincUsername = properties.getLoincUsername() == null ? "" : properties.getLoincUsername();
        this.loincPassword = properties.getLoincPassword() == null ? "" : properties.getLoincPassword();
    }

    /** {@code suggest(query=..., fact_type=...)}. */
    public TerminologySuggestionResult suggest(String query, FactType factType) {
        if (!enabled) {
            return TerminologySuggestionResult.unavailable(DISABLED_MESSAGE);
        }
        if (factType == FactType.MEDICATION) {
            return rxnorm(query);
        }
        if (factType == FactType.OBSERVATION) {
            return loinc(query);
        }
        return TerminologySuggestionResult.unavailable(UNSUPPORTED_MESSAGE);
    }

    /**
     * {@code _rxnorm}. RxNav returns {@code approximateGroup.candidate}, which is an object rather
     * than an array when there is exactly one match; both spellings are accepted.
     */
    private TerminologySuggestionResult rxnorm(String query) {
        JsonNode payload;
        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("term", query);
            params.put("maxEntries", Integer.toString(maxResults));
            params.put("option", "1");
            payload = getJson(RXNAV_APPROXIMATE_URL, params, null);
        } catch (TerminologyTransportException exception) {
            return TerminologySuggestionResult.unavailable(RXNORM_UNREACHABLE_MESSAGE);
        }

        JsonNode group = payload.path("approximateGroup");
        JsonNode candidates = group.isObject() ? group.path("candidate") : null;
        List<JsonNode> rows = new ArrayList<>();
        if (candidates != null && candidates.isObject()) {
            rows.add(candidates);
        } else if (candidates != null && candidates.isArray()) {
            candidates.forEach(rows::add);
        }

        List<TerminologySuggestion> suggestions = new ArrayList<>();
        for (JsonNode candidate : rows) {
            if (!candidate.isObject()
                    || !isTruthy(candidate.get("rxcui"))
                    || !isTruthy(candidate.get("name"))) {
                continue;
            }
            JsonNode source = candidate.get("source");
            JsonNode score = candidate.get("score");
            suggestions.add(
                    new TerminologySuggestion(
                            "rxnorm",
                            asText(candidate.get("rxcui")),
                            asText(candidate.get("name")),
                            isTruthy(source) ? asText(source) : null,
                            null,
                            score == null || score.isNull() ? null : asDouble(score)));
        }
        return new TerminologySuggestionResult(limit(suggestions), List.of());
    }

    /** {@code _loinc}. */
    private TerminologySuggestionResult loinc(String query) {
        if (loincUsername.isEmpty() || loincPassword.isEmpty()) {
            return TerminologySuggestionResult.unavailable(LOINC_MISSING_CREDENTIALS_MESSAGE);
        }
        JsonNode payload;
        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("query", query);
            params.put("rows", Integer.toString(maxResults));
            params.put("offset", "0");
            payload = getJson(LOINC_SEARCH_URL, params, basicAuth());
        } catch (TerminologyTransportException exception) {
            return TerminologySuggestionResult.unavailable(LOINC_UNREACHABLE_MESSAGE);
        }

        List<TerminologySuggestion> suggestions = new ArrayList<>();
        for (JsonNode row : loincRows(payload)) {
            TerminologySuggestion suggestion = loincSuggestion(row);
            if (suggestion != null) {
                suggestions.add(suggestion);
            }
        }
        return new TerminologySuggestionResult(limit(suggestions), List.of());
    }

    /**
     * {@code _get_json}.
     *
     * <p>Any non-2xx status, transport error or timeout becomes a {@link
     * TerminologyTransportException}, which the callers translate into an unavailable source - the
     * counterpart of catching {@code httpx.HTTPError} around {@code raise_for_status()}. A
     * successful response whose body is not a JSON object is treated as an empty object, exactly as
     * Python did.
     */
    private JsonNode getJson(String url, Map<String, String> params, String authorization) {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(URI.create(url + "?" + encode(params)))
                        .timeout(Duration.ofMillis(Math.max(1L, Math.round(timeoutSeconds * 1_000))))
                        .header("Accept", "application/json")
                        .GET();
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }

        HttpResponse<String> response;
        try {
            response =
                    httpClient.send(
                            builder.build(),
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new TerminologyTransportException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TerminologyTransportException(exception);
        }
        if (response.statusCode() >= 400) {
            throw new TerminologyTransportException(
                    new IOException("status " + response.statusCode()));
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(response.body());
        } catch (IOException exception) {
            // `response.json()` raised a decode error, which is not an httpx.HTTPError and so was
            // never caught. The behaviour is preserved rather than quietly improved.
            throw new IllegalStateException("Terminology response was not valid JSON", exception);
        }
        return payload != null && payload.isObject()
                ? payload
                : objectMapper.createObjectNode();
    }

    /** {@code _loinc_rows}: the first of the four known list-valued keys wins. */
    static List<JsonNode> loincRows(JsonNode payload) {
        for (String key : LOINC_ROW_KEYS) {
            JsonNode rows = payload.get(key);
            if (rows != null && rows.isArray()) {
                List<JsonNode> objects = new ArrayList<>();
                for (JsonNode row : rows) {
                    if (row.isObject()) {
                        objects.add(row);
                    }
                }
                return objects;
            }
        }
        return List.of();
    }

    /**
     * {@code _loinc_suggestion}: the LOINC search returns different key casings across versions, so
     * each field falls through a list of aliases. A row without both a code and a label is dropped
     * rather than filled in.
     */
    static TerminologySuggestion loincSuggestion(JsonNode row) {
        JsonNode code = firstTruthy(row, "LOINC_NUM", "loinc_num", "code");
        JsonNode label =
                firstTruthy(row, "LONG_COMMON_NAME", "long_common_name", "display", "COMPONENT");
        if (code == null || label == null) {
            return null;
        }
        JsonNode unit = firstTruthy(row, "EXAMPLE_UCUM_UNITS", "example_ucum_units");
        JsonNode detail = firstTruthy(row, "SHORTNAME", "shortname", "CLASS");
        return new TerminologySuggestion(
                "loinc",
                asText(code),
                asText(label),
                detail == null ? null : asText(detail),
                unit == null ? null : asText(unit),
                null);
    }

    private List<TerminologySuggestion> limit(List<TerminologySuggestion> suggestions) {
        return suggestions.size() <= maxResults
                ? suggestions
                : new ArrayList<>(suggestions.subList(0, maxResults));
    }

    private String basicAuth() {
        String credentials = loincUsername + ":" + loincPassword;
        return "Basic "
                + Base64.getEncoder()
                        .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    /** Matches {@code urlencode}, which httpx uses for query parameters: a space becomes {@code +}. */
    private static String encode(Map<String, String> params) {
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return query.toString();
    }

    /** The first alias whose value is truthy in the Python sense, or {@code null}. */
    private static JsonNode firstTruthy(JsonNode row, String... keys) {
        for (String key : keys) {
            JsonNode value = row.get(key);
            if (isTruthy(value)) {
                return value;
            }
        }
        return null;
    }

    /** Python truthiness: null, an empty string, zero and false are all falsy. */
    private static boolean isTruthy(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return false;
        }
        if (value.isTextual()) {
            return !value.asText().isEmpty();
        }
        if (value.isNumber()) {
            return value.asDouble() != 0.0d;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        return !value.isEmpty();
    }

    private static String asText(JsonNode value) {
        return value.isTextual() ? value.asText() : value.toString();
    }

    /** {@code float(value)}: RxNav has returned the score both as a number and as a string. */
    private static Double asDouble(JsonNode value) {
        if (value.isNumber()) {
            return value.asDouble();
        }
        return Double.valueOf(value.asText().trim());
    }

    /** Marks a lookup that could not complete; never escapes this class. */
    private static final class TerminologyTransportException extends RuntimeException {

        private TerminologyTransportException(Throwable cause) {
            super(cause);
        }
    }
}
