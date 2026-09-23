package com.trialsync.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.config.TrialSyncProperties;
import com.trialsync.backend.dto.chat.ScreeningChatCitationRead;
import com.trialsync.backend.dto.chat.ScreeningChatMessageCreate;
import com.trialsync.backend.dto.chat.ScreeningChatMessageRead;
import com.trialsync.backend.dto.chat.ScreeningChatProviderRead;
import com.trialsync.backend.dto.chat.ScreeningConversationRead;
import com.trialsync.backend.dto.screening.ScreeningCountsResponse;
import com.trialsync.backend.entity.CriterionEvaluation;
import com.trialsync.backend.entity.Screening;
import com.trialsync.backend.entity.ScreeningChatMessage;
import com.trialsync.backend.entity.TimestampedEntity;
import com.trialsync.backend.entity.User;
import com.trialsync.backend.nlp.CanonicalExplainer;
import com.trialsync.backend.nlp.ChatAnswer;
import com.trialsync.backend.nlp.ChatEvaluation;
import com.trialsync.backend.nlp.ChatMessage;
import com.trialsync.backend.nlp.ChatServiceProviderSource;
import com.trialsync.backend.nlp.Citation;
import com.trialsync.backend.nlp.Latency;
import com.trialsync.backend.nlp.ProviderCallException;
import com.trialsync.backend.nlp.ScreeningChatContext;
import com.trialsync.backend.nlp.ScreeningChatPolicy;
import com.trialsync.backend.nlp.ScreeningChatProvider;
import com.trialsync.backend.nlp.Texts;
import com.trialsync.backend.repository.ScreeningChatMessageRepository;
import com.trialsync.backend.security.SecurityContext;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The screening explanation assistant. Port of the conversation endpoints of
 * {@code trialsync.api.screenings} together with {@code trialsync.nlp.chat}.
 *
 * <p>This service explains a decision that has already been made. It reads a stored screening and
 * its stored criterion evaluations, and it writes conversation turns. It never calls the screening
 * engine and never updates a screening, an evaluation or a snapshot, so no question asked here -
 * and no answer given here - can change an eligibility result.
 *
 * <p>Three guards make that boundary real:
 *
 * <ul>
 *   <li>every question is passed through {@link ScreeningChatPolicy#mustRefuse} before a provider is
 *       consulted, and capability and criterion-state questions never reach a provider at all;
 *   <li>every answer is passed through {@link ScreeningChatPolicy#validateAnswer}, which re-resolves
 *       each citation against the stored evaluations and downgrades an answer that cites anything
 *       it cannot prove;
 *   <li>a provider failure either surfaces as an {@code ASSISTANT_*} error or falls back to the
 *       deterministic {@link CanonicalExplainer}. Nothing is invented on the model's behalf.
 * </ul>
 */
@Service
public class ChatService {

    /**
     * {@code logging.getLogger("trialsync.chat.metrics")}. The name is kept so an existing log
     * pipeline keeps matching on it.
     */
    private static final Logger CHAT_METRICS = LoggerFactory.getLogger("trialsync.chat.metrics");

    /** {@code str(item.get("label", "Criterion"))} when a stored citation carries no label. */
    private static final String DEFAULT_CITATION_LABEL = "Criterion";

    /** {@code message.provider or "unknown"}. */
    private static final String UNKNOWN_PROVIDER = "unknown";

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    private static final String GROQ_PROVIDER = "groq";

    /** {@code code_map}: provider failure code to public error code and HTTP status. */
    private static final Map<String, ProviderFailure> CODE_MAP =
            Map.of(
                    "PROVIDER_TIMEOUT", new ProviderFailure("ASSISTANT_TIMEOUT", 504),
                    "PROVIDER_RATE_LIMITED", new ProviderFailure("ASSISTANT_RATE_LIMITED", 429),
                    "PROVIDER_RESPONSE_INVALID",
                            new ProviderFailure("ASSISTANT_RESPONSE_INVALID", 502),
                    "PROVIDER_ERROR", new ProviderFailure("ASSISTANT_PROVIDER_ERROR", 502),
                    "ASSISTANT_DISABLED", new ProviderFailure("ASSISTANT_DISABLED", 503));

    /** {@code code_map.get(exception.code, ("ASSISTANT_PROVIDER_ERROR", 502))}. */
    private static final ProviderFailure DEFAULT_FAILURE =
            new ProviderFailure("ASSISTANT_PROVIDER_ERROR", 502);

    private final ScreeningService screeningService;
    private final ScreeningChatMessageRepository chatMessages;
    private final ChatServiceProviderSource providerSource;
    private final TrialSyncProperties properties;
    private final ObjectMapper objectMapper;

    public ChatService(
            ScreeningService screeningService,
            ScreeningChatMessageRepository chatMessages,
            ChatServiceProviderSource providerSource,
            TrialSyncProperties properties,
            ObjectMapper objectMapper) {
        this.screeningService = screeningService;
        this.chatMessages = chatMessages;
        this.providerSource = providerSource;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------ endpoints

    /**
     * Port of {@code get_screening_conversation}.
     *
     * <p>The citation labels are rebuilt here from the stored criterion source text rather than read
     * out of the saved citation rows, keyed by evaluation id. A citation whose evaluation no longer
     * exists therefore falls back to its stored label, and the label a client renders always traces
     * to a row in {@code criterion_evaluations}.
     */
    @Transactional(readOnly = true)
    public ScreeningConversationRead getConversation(UUID screeningId) {
        User user = SecurityContext.require();
        Screening screening = screeningService.ownedScreening(user.getId(), screeningId);
        int limit = properties.getScreeningChatMaxMessages();
        List<ScreeningChatMessage> messages = recentChat(screening.getId(), limit);
        ScreeningChatContext context = chatContext(screening);

        Map<String, String> citationLabels = new HashMap<>();
        for (ChatEvaluation evaluation : context.evaluations()) {
            citationLabels.put(
                    evaluation.evaluationId(), String.valueOf(evaluation.sourceText()));
        }

        List<ScreeningChatMessageRead> reads = new ArrayList<>(messages.size());
        for (ScreeningChatMessage message : messages) {
            reads.add(toMessageRead(message, null, citationLabels));
        }
        return new ScreeningConversationRead(
                screening.getId(),
                reads,
                providerRead(providerSource.get()),
                ScreeningChatPolicy.contextualSuggestions(context),
                limit,
                properties.getScreeningChatMessageMaxChars());
    }

    /**
     * Port of {@code create_screening_chat_message}.
     *
     * <p>The transaction spans the provider call because Python holds its session across the same
     * await, and because the answer has to be validated against evaluations loaded from this very
     * screening. Both turns are written together or not at all: an {@link ApplicationError} raised
     * for a provider failure is unchecked, so it rolls the unit back exactly as Python's
     * {@code except: rollback; raise} does - and it is raised before anything is written anyway.
     */
    @Transactional
    public ScreeningChatMessageRead createMessage(
            UUID screeningId, ScreeningChatMessageCreate payload) {
        User user = SecurityContext.require();
        Screening screening = screeningService.ownedScreening(user.getId(), screeningId);

        int maxMessageChars = properties.getScreeningChatMessageMaxChars();
        String message = payload.message().strip();
        if (message.isEmpty() || Texts.length(message) > maxMessageChars) {
            throw ApplicationError.unprocessable(
                    "ASSISTANT_MESSAGE_TOO_LONG",
                    "Messages must contain at most " + maxMessageChars + " characters.",
                    "message");
        }

        int maxMessages = properties.getScreeningChatMaxMessages();
        List<ScreeningChatMessage> historyRows = recentChat(screening.getId(), maxMessages);
        List<ChatMessage> history = new ArrayList<>(historyRows.size());
        for (ScreeningChatMessage item : historyRows) {
            history.add(new ChatMessage(item.getRole(), item.getContent()));
        }

        ScreeningChatContext context = chatContext(screening);
        ScreeningChatProvider provider = providerSource.get();
        ScreeningChatProvider providerUsed = provider;
        boolean fallbackUsed = false;
        long started = Latency.start();

        String question = Texts.casefold(message);
        ChatAnswer rawAnswer;
        ChatAnswer answer;
        if (ScreeningChatPolicy.isScreeningAssistantCapabilityQuestion(question)
                || ScreeningChatPolicy.isCriterionStateQuestion(question)) {
            // Answered from the stored evaluations only, so the enumeration is always complete and
            // no provider is billed, prompted or trusted for it.
            providerUsed = new CanonicalExplainer();
            rawAnswer = providerUsed.answer(context, history, message);
            answer = ScreeningChatPolicy.validateAnswer(rawAnswer, context);
        } else {
            try {
                rawAnswer = provider.answer(context, history, message);
                answer = ScreeningChatPolicy.validateAnswer(rawAnswer, context);
            } catch (ProviderCallException exception) {
                ProviderFailure failure =
                        CODE_MAP.getOrDefault(exception.getCode(), DEFAULT_FAILURE);
                // The provider's own failure code is recorded, never its payload, prompt or any
                // patient text. `answer_state` is always absent on this path.
                CHAT_METRICS.warn(
                        "screening_chat_provider_failed provider={} model_id={} prompt_version={}"
                                + " latency_ms={} validation_outcome={} answer_state=null",
                        provider.providerName(),
                        provider.modelId(),
                        ScreeningChatPolicy.CHAT_PROMPT_VERSION,
                        Latency.millisSince(started),
                        exception.getCode());
                if (!GROQ_PROVIDER.equals(provider.providerName())) {
                    // The deterministic and disabled providers have nothing to fall back to: a
                    // disabled assistant reports ASSISTANT_DISABLED rather than quietly answering.
                    throw new ApplicationError(
                            failure.code(), exception.getMessage(), failure.statusCode());
                }
                // Groq is unreachable or misbehaving. The stored record can still be explained
                // deterministically, so it is - no model output is fabricated.
                providerUsed = new CanonicalExplainer();
                rawAnswer = providerUsed.answer(context, history, message);
                answer = ScreeningChatPolicy.validateAnswer(rawAnswer, context);
                fallbackUsed = true;
            }
        }

        OffsetDateTime now = TimestampedEntity.nowUtc();
        ScreeningChatMessage userMessage =
                new ScreeningChatMessage(screening.getId(), ROLE_USER, message);
        userMessage.setAnswerState(null);
        userMessage.setCitationsJson("[]");
        userMessage.setCreatedAt(now);

        ScreeningChatMessage assistantMessage =
                new ScreeningChatMessage(
                        screening.getId(),
                        ROLE_ASSISTANT,
                        Texts.truncate(
                                answer.answer(), properties.getScreeningChatMaxAnswerChars()));
        assistantMessage.setAnswerState(answer.answerState());
        assistantMessage.setCitationsJson(writeCitations(answer.citations()));
        assistantMessage.setProvider(providerUsed.providerName());
        assistantMessage.setModelId(providerUsed.modelId());
        assistantMessage.setPromptVersion(ScreeningChatPolicy.CHAT_PROMPT_VERSION);
        // `now + timedelta(microseconds=1)`: the assistant turn must sort after the question it
        // answers even when the clock does not advance between the two inserts.
        assistantMessage.setCreatedAt(now.plusNanos(1_000L));

        chatMessages.saveAllAndFlush(List.of(userMessage, assistantMessage));
        trimConversation(screening.getId(), maxMessages);

        String validationOutcome;
        if (fallbackUsed) {
            validationOutcome = "provider_fallback";
        } else if (ChatAnswer.SUPPORTED.equals(rawAnswer.answerState())
                && ChatAnswer.INSUFFICIENT_EVIDENCE.equals(answer.answerState())) {
            validationOutcome = "safe_downgrade";
        } else {
            validationOutcome = "valid";
        }
        CHAT_METRICS.info(
                "screening_chat_completed provider={} model_id={} prompt_version={} latency_ms={}"
                        + " validation_outcome={} answer_state={} citation_count={}",
                providerUsed.providerName(),
                providerUsed.modelId(),
                ScreeningChatPolicy.CHAT_PROMPT_VERSION,
                Latency.millisSince(started),
                validationOutcome,
                answer.answerState(),
                answer.citations().size());

        return toMessageRead(assistantMessage, answer.suggestedQuestions(), null);
    }

    /**
     * Port of {@code clear_screening_conversation}: every turn for this screening is deleted.
     *
     * <p>Only {@code screening_chat_messages} rows are removed. The screening, its evaluations and
     * its snapshot are untouched, so clearing a conversation cannot alter what the record says.
     */
    @Transactional
    public void clearConversation(UUID screeningId) {
        User user = SecurityContext.require();
        Screening screening = screeningService.ownedScreening(user.getId(), screeningId);
        chatMessages.deleteByScreeningId(screening.getId());
    }

    // ------------------------------------------------------------------ conversation storage

    /**
     * Port of {@code _recent_chat}: the newest {@code limit} turns, returned oldest-first.
     *
     * <p>The query orders descending so the <em>most recent</em> window is taken, then the list is
     * reversed for display. Taking the oldest {@code limit} rows instead would show a stale prefix
     * of a trimmed conversation.
     */
    private List<ScreeningChatMessage> recentChat(UUID screeningId, int limit) {
        List<ScreeningChatMessage> rows =
                new ArrayList<>(
                        chatMessages.findByScreeningIdOrderByCreatedAtDescIdDesc(
                                screeningId, PageRequest.of(0, limit)));
        Collections.reverse(rows);
        return rows;
    }

    /**
     * Trims the conversation to its configured maximum.
     *
     * <p>The retained ids are read back after the flush, so the two turns just written are always
     * among them and the delete list is never everything. The guard on an empty keep-list is
     * defensive only: {@code id not in ()} is invalid SQL, and the situation cannot arise.
     */
    private void trimConversation(UUID screeningId, int maxMessages) {
        List<UUID> keepIds =
                chatMessages.findRecentIds(screeningId, PageRequest.of(0, maxMessages));
        if (!keepIds.isEmpty()) {
            chatMessages.deleteByScreeningIdAndIdNotIn(screeningId, keepIds);
        }
    }

    // ------------------------------------------------------------------ context + responses

    /**
     * Port of {@code _chat_context}.
     *
     * <p>Everything in the returned context is copied out of stored rows: the overall state and the
     * counts come from the screening the engine already decided, and each evaluation carries the
     * criterion text, result, reason code and canonical explanation exactly as they were written.
     * {@code evidence_ids} is the flattened list of {@code fact_id} values found in the stored
     * evidence, and it is the list a provider's citations are later checked against.
     */
    private ScreeningChatContext chatContext(Screening screening) {
        List<ChatEvaluation> evaluations = new ArrayList<>();
        for (CriterionEvaluation item : screening.getEvaluations()) {
            JsonNode evidence = readJson(item.getEvidenceJson());
            JsonNode missingInformation = readJson(item.getMissingInformationJson());
            evaluations.add(
                    new ChatEvaluation(
                            String.valueOf(item.getCriterionId()),
                            String.valueOf(item.getId()),
                            item.getCriterionOrder(),
                            item.getCriterionKind().value(),
                            item.getCriterionSourceText(),
                            item.getResult().value(),
                            item.getReasonCode(),
                            item.getCanonicalExplanation(),
                            factIds(evidence),
                            evidence,
                            missingInformation));
        }

        ScreeningCountsResponse counts = screeningService.counts(screening);
        Map<String, Integer> countsByState = new LinkedHashMap<>();
        countsByState.put("pass", counts.passCount());
        countsByState.put("fail", counts.failCount());
        countsByState.put("unknown", counts.unknownCount());

        Map<String, String> versions = new LinkedHashMap<>();
        versions.put("engine", screening.getEngineVersion());
        versions.put("dsl", screening.getDslVersion());
        versions.put("patient_snapshot", String.valueOf(screening.getPatientSnapshotId()));
        versions.put("trial_version", String.valueOf(screening.getTrialVersionId()));

        return new ScreeningChatContext(
                String.valueOf(screening.getId()),
                screening.getOverallState().value(),
                countsByState,
                evaluations,
                versions);
    }

    /**
     * {@code [str(value["fact_id"]) for value in evidence_json if isinstance(value, dict) and
     * value.get("fact_id") is not None]}.
     */
    private static List<String> factIds(JsonNode evidence) {
        List<String> ids = new ArrayList<>();
        if (evidence == null || !evidence.isArray()) {
            return ids;
        }
        for (JsonNode value : evidence) {
            if (value.isObject() && value.hasNonNull("fact_id")) {
                ids.add(value.get("fact_id").asText());
            }
        }
        return ids;
    }

    /** Port of {@code _provider_read}. */
    private static ScreeningChatProviderRead providerRead(ScreeningChatProvider provider) {
        return new ScreeningChatProviderRead(
                provider.enabled(),
                provider.providerName(),
                provider.modelId(),
                ScreeningChatPolicy.CHAT_PROMPT_VERSION);
    }

    /**
     * Port of {@code _message_read}.
     *
     * <p>{@code suggestedQuestions} is never stored: it is supplied per response so the prompts
     * always reflect the screening's current counts. {@code citationLabels} is supplied only by the
     * conversation read, where labels are re-derived from the stored criterion source text; the
     * message-creation path passes {@code null} and keeps the labels
     * {@link ScreeningChatPolicy#validateAnswer} already replaced.
     */
    private ScreeningChatMessageRead toMessageRead(
            ScreeningChatMessage message,
            List<String> suggestedQuestions,
            Map<String, String> citationLabels) {
        ScreeningChatProviderRead provider = null;
        if (ROLE_ASSISTANT.equals(message.getRole())) {
            String providerName = message.getProvider();
            String promptVersion = message.getPromptVersion();
            provider =
                    new ScreeningChatProviderRead(
                            true,
                            providerName == null || providerName.isEmpty()
                                    ? UNKNOWN_PROVIDER
                                    : providerName,
                            message.getModelId(),
                            promptVersion == null || promptVersion.isEmpty()
                                    ? ScreeningChatPolicy.CHAT_PROMPT_VERSION
                                    : promptVersion);
        }
        return new ScreeningChatMessageRead(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getAnswerState(),
                readCitations(message.getCitationsJson(), citationLabels),
                provider,
                message.getCreatedAt(),
                suggestedQuestions);
    }

    /**
     * Rebuilds the stored citations for the wire.
     *
     * <p>The identifiers are parsed as {@code UUID}, which is the last line of the provenance guard:
     * a citation that was somehow persisted with an unresolvable identifier cannot be serialised at
     * all. Well-formed rows only ever come from {@link ScreeningChatPolicy#validateAnswer}.
     */
    private List<ScreeningChatCitationRead> readCitations(
            String citationsJson, Map<String, String> citationLabels) {
        JsonNode stored = readJson(citationsJson);
        if (stored == null || !stored.isArray()) {
            return List.of();
        }
        List<ScreeningChatCitationRead> citations = new ArrayList<>();
        for (JsonNode item : stored) {
            String evaluationId = textOrEmpty(item.get("evaluation_id"));
            JsonNode labelNode = item.get("label");
            String storedLabel =
                    labelNode == null || labelNode.isNull()
                            ? DEFAULT_CITATION_LABEL
                            : labelNode.asText();
            String label =
                    citationLabels == null
                            ? storedLabel
                            : citationLabels.getOrDefault(evaluationId, storedLabel);

            List<String> evidenceIds = new ArrayList<>();
            JsonNode evidenceNode = item.get("evidence_ids");
            if (evidenceNode != null && evidenceNode.isArray()) {
                for (JsonNode value : evidenceNode) {
                    evidenceIds.add(value.asText());
                }
            }
            citations.add(
                    new ScreeningChatCitationRead(
                            UUID.fromString(textOrEmpty(item.get("criterion_id"))),
                            UUID.fromString(evaluationId),
                            evidenceIds,
                            label));
        }
        return citations;
    }

    /**
     * Reads a stored identifier for parsing.
     *
     * <p>A citation row that is missing an identifier, or holds a non-string one, yields the empty
     * string so {@link UUID#fromString} rejects it. That mirrors Python: Pydantic's UUID field would
     * fail validation and the request would end as a server error rather than as a partial response.
     */
    private static String textOrEmpty(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText();
    }

    /** {@code [item.model_dump(mode="json") for item in answer.citations]}. */
    private String writeCitations(List<Citation> citations) {
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Chat citations could not be serialised", exception);
        }
    }

    /**
     * Parses one of the stored JSON array columns.
     *
     * <p>All three columns this reads - evidence, missing information and citations - are written as
     * JSON arrays and are non-null in the schema. A blank column is therefore read as an empty
     * array rather than as null, so the value embedded in a provider prompt is {@code []} exactly as
     * Python's {@code evidence_json} would have been.
     */
    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createArrayNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored chat payload could not be parsed", exception);
        }
    }

    /** One entry of {@code code_map}. */
    private record ProviderFailure(String code, int statusCode) {}
}
