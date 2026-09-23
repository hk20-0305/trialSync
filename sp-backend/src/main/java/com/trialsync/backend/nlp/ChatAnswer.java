package com.trialsync.backend.nlp;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The bounded answer shape every explanation provider must produce.
 *
 * <p>Port of {@code trialsync.nlp.chat.ChatAnswer}. {@code answerState} is one of
 * {@code supported}, {@code insufficient_evidence} or {@code refused} - deliberately not a verdict
 * vocabulary. Nothing here can express eligibility; the screening's {@code overall_state} is decided
 * only by the deterministic engine and is merely quoted back by the explainer.
 */
public record ChatAnswer(
        @JsonProperty("answer_state") String answerState,
        @JsonProperty("answer") String answer,
        @JsonProperty("citations") List<Citation> citations,
        @JsonProperty("suggested_questions") List<String> suggestedQuestions) {

    public static final String SUPPORTED = "supported";
    public static final String INSUFFICIENT_EVIDENCE = "insufficient_evidence";
    public static final String REFUSED = "refused";

    public ChatAnswer {
        citations = citations == null ? List.of() : List.copyOf(citations);
        suggestedQuestions =
                suggestedQuestions == null ? List.of() : List.copyOf(suggestedQuestions);
    }

    /** Convenience for the answers Python built with only a state, prose and follow-up prompts. */
    public ChatAnswer(String answerState, String answer, List<String> suggestedQuestions) {
        this(answerState, answer, List.of(), suggestedQuestions);
    }

    /** {@code answer.model_copy(update={"citations": ..., "suggested_questions": ...})}. */
    public ChatAnswer withCitationsAndSuggestions(
            List<Citation> replacementCitations, List<String> replacementSuggestions) {
        return new ChatAnswer(answerState, answer, replacementCitations, replacementSuggestions);
    }
}
