package com.trialsync.backend.nlp;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Local re-validation of a provider's structured answer, replacing
 * {@code ChatAnswer.model_validate(...)}.
 *
 * <p>The provider is asked for a strict JSON schema, but the schema is the provider's promise, not
 * ours. Every bound the Pydantic model declared is re-checked here on the way in - closed objects,
 * the three permitted answer states, the length limits and the collection caps - because a
 * structured-output contract is not a security control.
 *
 * <p>Any deviation raises {@code PROVIDER_RESPONSE_INVALID}, which the API maps to
 * {@code ASSISTANT_RESPONSE_INVALID}. The malformed payload is never surfaced or logged.
 */
public final class ChatAnswerCodec {

    private static final Set<String> ANSWER_FIELDS =
            Set.of("answer_state", "answer", "citations", "suggested_questions");
    private static final Set<String> CITATION_FIELDS =
            Set.of("criterion_id", "evaluation_id", "evidence_ids", "label");
    private static final Set<String> ANSWER_STATES =
            Set.of(ChatAnswer.SUPPORTED, ChatAnswer.INSUFFICIENT_EVIDENCE, ChatAnswer.REFUSED);

    private static final int MAX_ANSWER_CHARS = 4_000;
    private static final int MAX_EVIDENCE_IDS = 20;

    private ChatAnswerCodec() {}

    /** Parses and bounds-checks a provider payload, or fails with a provider-neutral code. */
    public static ChatAnswer parse(JsonNode payload) {
        try {
            return read(payload);
        } catch (RuntimeException exception) {
            throw new ProviderCallException(
                    "PROVIDER_RESPONSE_INVALID", "The assistant response failed validation.");
        }
    }

    private static ChatAnswer read(JsonNode payload) {
        requireClosedObject(payload, ANSWER_FIELDS, "answer_state", "answer");

        String answerState = requireText(payload.get("answer_state"));
        if (!ANSWER_STATES.contains(answerState)) {
            throw new IllegalArgumentException("answer_state is not permitted");
        }
        String answer = requireText(payload.get("answer"));
        int answerLength = Texts.length(answer);
        if (answerLength < 1 || answerLength > MAX_ANSWER_CHARS) {
            throw new IllegalArgumentException("answer length is out of bounds");
        }

        List<Citation> citations = new ArrayList<>();
        JsonNode citationsNode = payload.get("citations");
        if (citationsNode != null && !citationsNode.isNull()) {
            if (!citationsNode.isArray()) {
                throw new IllegalArgumentException("citations must be an array");
            }
            if (citationsNode.size() > ScreeningChatPolicy.MAX_CITATIONS) {
                throw new IllegalArgumentException("too many citations");
            }
            for (JsonNode node : citationsNode) {
                citations.add(readCitation(node));
            }
        }

        List<String> suggestions = new ArrayList<>();
        JsonNode suggestionsNode = payload.get("suggested_questions");
        if (suggestionsNode != null && !suggestionsNode.isNull()) {
            if (!suggestionsNode.isArray()) {
                throw new IllegalArgumentException("suggested_questions must be an array");
            }
            if (suggestionsNode.size() > ScreeningChatPolicy.MAX_SUGGESTIONS) {
                throw new IllegalArgumentException("too many suggested questions");
            }
            for (JsonNode node : suggestionsNode) {
                suggestions.add(requireText(node));
            }
        }

        return new ChatAnswer(answerState, answer, citations, suggestions);
    }

    private static Citation readCitation(JsonNode node) {
        requireClosedObject(node, CITATION_FIELDS, "criterion_id", "evaluation_id", "label");

        String label = requireText(node.get("label"));
        int labelLength = Texts.length(label);
        if (labelLength < 1 || labelLength > ScreeningChatPolicy.MAX_LABEL_CHARS) {
            throw new IllegalArgumentException("label length is out of bounds");
        }

        List<String> evidenceIds = new ArrayList<>();
        JsonNode evidenceNode = node.get("evidence_ids");
        if (evidenceNode != null && !evidenceNode.isNull()) {
            if (!evidenceNode.isArray()) {
                throw new IllegalArgumentException("evidence_ids must be an array");
            }
            if (evidenceNode.size() > MAX_EVIDENCE_IDS) {
                throw new IllegalArgumentException("too many evidence ids");
            }
            for (JsonNode value : evidenceNode) {
                evidenceIds.add(requireText(value));
            }
        }

        return new Citation(
                requireText(node.get("criterion_id")),
                requireText(node.get("evaluation_id")),
                evidenceIds,
                label);
    }

    /** {@code extra="forbid"} plus the required-field check Pydantic applied. */
    private static void requireClosedObject(
            JsonNode node, Set<String> permitted, String... required) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("expected an object");
        }
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            if (!permitted.contains(names.next())) {
                throw new IllegalArgumentException("unexpected field");
            }
        }
        for (String name : required) {
            if (!node.hasNonNull(name)) {
                throw new IllegalArgumentException("missing field " + name);
            }
        }
    }

    private static String requireText(JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw new IllegalArgumentException("expected a string");
        }
        return node.asText();
    }
}
