package com.trialsync.backend.nlp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The safety boundary of the explanation layer, ported from the module-level helpers of
 * {@code trialsync.nlp.chat}.
 *
 * <p>Two rules are enforced here and nowhere else:
 *
 * <ol>
 *   <li><b>Refusal.</b> Medical, dosing, cross-record and prompt-injection requests are refused
 *       before any provider is consulted, by literal regular expressions rather than by asking a
 *       model to police itself.
 *   <li><b>Provenance.</b> {@link #validateAnswer} re-resolves every citation against the stored
 *       evaluations. A citation that names an unknown evaluation, contradicts the stored
 *       criterion, or claims evidence the evaluation does not hold is dropped. If that leaves a
 *       {@code supported} answer with fewer citations than it started with, the whole answer is
 *       replaced by the fixed {@link #SAFE_INSUFFICIENT_ANSWER} at
 *       {@code insufficient_evidence} - the model's prose is discarded rather than shown with
 *       weakened support.
 * </ol>
 *
 * <p>Every pattern is matched against case-folded text, exactly as the Python code did, and none of
 * them carries the ignore-case flag; the one exception is the bounded-suggestion vocabulary check,
 * which Python ran case-insensitively over the un-folded prompt.
 */
public final class ScreeningChatPolicy {

    /** Prompt revision recorded on every assistant turn. */
    public static final String CHAT_PROMPT_VERSION = "screening-chat-v1";

    /** The single sentence used whenever the record cannot support an answer. */
    public static final String SAFE_INSUFFICIENT_ANSWER =
            "The stored screening record does not contain enough information to answer that "
                    + "question. Review the canonical criterion explanations and recorded evidence.";

    /** Refusal text for medical, dosing, cross-record and prompt-injection requests. */
    public static final String REFUSAL_ANSWER =
            "I can only explain this stored educational screening result. I cannot give medical, "
                    + "treatment, diagnosis, enrollment, or cross-record guidance.";

    /** Capability answer when the screening has at least one criterion evaluation. */
    public static final String CAPABILITY_ANSWER =
            "I explain this selected screening result: why criteria passed, failed, or remain "
                    + "unknown; what recorded evidence supports them; and what information is still "
                    + "needed. I cannot change the result or provide medical or enrollment advice.";

    /** Capability answer when the screening has no criterion evaluations to discuss. */
    public static final String CAPABILITY_WITHOUT_EVALUATIONS_ANSWER =
            "I can explain the selected screening's criteria, evidence, and missing information, "
                    + "but this record has no criterion evaluations to discuss.";

    /** Maximum citations, and maximum criteria the canonical explainer will enumerate. */
    public static final int MAX_CITATIONS = 20;

    /** Maximum follow-up prompts returned with any answer. */
    public static final int MAX_SUGGESTIONS = 3;

    /** Maximum length of a citation label, in code points. */
    public static final int MAX_LABEL_CHARS = 200;

    private static final Pattern CAPABILITY_QUESTION =
            Pattern.compile(
                    "\\b(?:what|how)\\b.{0,60}\\b(?:can|does)\\b.{0,60}"
                            + "\\b(?:assistant|chat|you)\\b.{0,60}\\b(?:do|help)\\b");

    private static final Pattern CRITERION_STATE_QUESTION =
            Pattern.compile(
                    "\\b(?:what|which|list|show)\\b.{0,80}"
                            + "\\b(?:criterion|criteria|criterias)\\b.{0,80}"
                            + "\\b(?:pass(?:ed|ing)?|fail(?:ed|ing)?|eligible|ineligible|unknown"
                            + "|missing)\\b");

    /**
     * The five refusal families, in the Python declaration order:
     *
     * <ol>
     *   <li>enrollment, treatment, medication and dosing recommendations;
     *   <li>diagnosis, clinical-validity and safety claims;
     *   <li>cross-record requests about another patient, trial, screening or record;
     *   <li>prompt injection: overriding instructions or rewriting a result, evidence or outcome;
     *   <li>prompt disclosure and plainly off-topic requests.
     * </ol>
     */
    private static final List<Pattern> REFUSAL_PATTERNS =
            List.of(
                    Pattern.compile(
                            "\\b(should|recommend)\\b.*\\b(enroll|treatment|medication|dose|take)\\b"),
                    Pattern.compile(
                            "\\b(diagnos(?:e|is)|clinically valid|medical advice|safe to)\\b"),
                    Pattern.compile(
                            "\\b(other|different|another)\\b.*\\b(patient|trial|screening|record)\\b"),
                    Pattern.compile(
                            "\\b(ignore|override|change|approve|rewrite)\\b.*"
                                    + "\\b(instruction|result|evidence|outcome)\\b"),
                    Pattern.compile("\\b(system prompt|hidden prompt|weather|sports|stock price)\\b"));

    private static final Pattern BOUNDED_SUGGESTION_VOCABULARY =
            Pattern.compile(
                    "\\b(result|screening|state|criterion|criteria|evidence|information|missing"
                            + "|unknown|pass|passed|fail|failed|snapshot|version)\\b",
                    Pattern.CASE_INSENSITIVE);

    private ScreeningChatPolicy() {}

    /**
     * Recognises a harmless question about what this assistant is for. Such questions are answered
     * by the deterministic explainer without consulting any provider.
     *
     * @param question the case-folded question
     */
    public static boolean isScreeningAssistantCapabilityQuestion(String question) {
        return question != null && CAPABILITY_QUESTION.matcher(question).find();
    }

    /**
     * Recognises a request to enumerate the criteria sitting in one stored result state. These are
     * answered from the stored evaluations directly, so the enumeration is always complete and
     * always accurate.
     *
     * @param question the case-folded question
     */
    public static boolean isCriterionStateQuestion(String question) {
        return question != null && CRITERION_STATE_QUESTION.matcher(question).find();
    }

    /**
     * The refusal gate. Runs before any provider call and again over every suggested follow-up.
     *
     * @param question the case-folded question
     */
    public static boolean mustRefuse(String question) {
        if (question == null) {
            return false;
        }
        for (Pattern pattern : REFUSAL_PATTERNS) {
            if (pattern.matcher(question).find()) {
                return true;
            }
        }
        return false;
    }

    /** {@code contextual_suggestions(context)}. */
    public static List<String> contextualSuggestions(ScreeningChatContext context) {
        return contextualSuggestions(context, null);
    }

    /**
     * Returns at most three deduplicated, screening-bounded follow-up questions.
     *
     * <p>The service's own prompts come first, then anything the provider proposed, then a fixed
     * tail so the list is never short. Provider suggestions are held to exactly the same bar as the
     * rest: refusal-gated, length-bounded, and required to mention the screening vocabulary, which
     * is why an off-topic or unsafe proposal silently disappears instead of being shown.
     */
    public static List<String> contextualSuggestions(
            ScreeningChatContext context, List<String> providerSuggestions) {
        List<String> prompts = new ArrayList<>();
        prompts.add("Why does this result have its current state?");
        if (context.count("unknown") != 0) {
            prompts.add("What information is missing?");
        } else if (context.count("fail") != 0) {
            prompts.add("Which criteria failed and why?");
        } else {
            prompts.add("Which criteria passed?");
        }
        if (providerSuggestions != null) {
            prompts.addAll(providerSuggestions);
        }
        prompts.add("Which criteria passed?");
        prompts.add("What recorded evidence supports this result?");
        prompts.add("Which criteria failed and why?");

        List<String> bounded = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String prompt : prompts) {
            if (prompt == null) {
                continue;
            }
            String normalized = Texts.collapseWhitespace(prompt);
            String key = Texts.casefold(normalized);
            if (seen.contains(key) || !isBoundedSuggestion(normalized)) {
                continue;
            }
            bounded.add(normalized);
            seen.add(key);
            if (bounded.size() == MAX_SUGGESTIONS) {
                break;
            }
        }
        return List.copyOf(bounded);
    }

    /** {@code _is_bounded_suggestion}: a short, safe, on-topic question ending in a question mark. */
    public static boolean isBoundedSuggestion(String prompt) {
        int length = Texts.length(prompt);
        if (length < 8 || length > 120 || !prompt.endsWith("?")) {
            return false;
        }
        if (mustRefuse(Texts.casefold(prompt))) {
            return false;
        }
        return BOUNDED_SUGGESTION_VOCABULARY.matcher(prompt).find();
    }

    /**
     * The provenance guard: {@code validate_answer}.
     *
     * <p>Each citation must name an evaluation that exists in this screening, must agree with that
     * evaluation's {@code criterion_id}, and may only claim evidence identifiers the evaluation
     * actually holds. Surviving citations have their label replaced by the stored criterion source
     * text, so the displayed label always comes from the database and never from the model.
     *
     * <p>A {@code supported} answer that loses any citation - or that arrived with none - is
     * downgraded wholesale to {@code insufficient_evidence} with the fixed safe sentence. Refused
     * and already-insufficient answers keep their state; their citations are still filtered.
     */
    public static ChatAnswer validateAnswer(ChatAnswer answer, ScreeningChatContext context) {
        Map<String, ChatEvaluation> evaluations = new HashMap<>();
        for (ChatEvaluation evaluation : context.evaluations()) {
            evaluations.put(evaluation.evaluationId(), evaluation);
        }

        List<Citation> valid = new ArrayList<>();
        for (Citation citation : answer.citations()) {
            ChatEvaluation evaluation = evaluations.get(citation.evaluationId());
            if (evaluation == null
                    || !String.valueOf(evaluation.criterionId()).equals(citation.criterionId())) {
                continue;
            }
            Set<String> evidenceIds = new HashSet<>(evaluation.evidenceIds());
            if (!evidenceIds.containsAll(citation.evidenceIds())) {
                continue;
            }
            valid.add(citation.withLabel(
                    Texts.truncate(String.valueOf(evaluation.sourceText()), MAX_LABEL_CHARS)));
        }

        if (ChatAnswer.SUPPORTED.equals(answer.answerState())
                && (valid.isEmpty() || valid.size() != answer.citations().size())) {
            return new ChatAnswer(
                    ChatAnswer.INSUFFICIENT_EVIDENCE,
                    SAFE_INSUFFICIENT_ANSWER,
                    contextualSuggestions(context));
        }
        return answer.withCitationsAndSuggestions(
                valid, contextualSuggestions(context, answer.suggestedQuestions()));
    }

    /**
     * {@code _citation}: builds a citation straight from a stored evaluation.
     *
     * <p>Used only by the deterministic explainer, whose citations are correct by construction.
     */
    public static Citation citationFor(ChatEvaluation evaluation) {
        return new Citation(
                String.valueOf(evaluation.criterionId()),
                String.valueOf(evaluation.evaluationId()),
                List.copyOf(evaluation.evidenceIds()),
                Texts.truncate(String.valueOf(evaluation.canonicalExplanation()), MAX_LABEL_CHARS));
    }
}
