package com.trialsync.backend.dto.chat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One stored conversation turn: {@code schemas.ScreeningChatMessageRead}.
 *
 * <p>{@code answerState} and {@code provider} are populated for assistant turns only. The state is
 * one of {@code supported}, {@code insufficient_evidence} or {@code refused} - never an eligibility
 * verdict. It describes the standing of the explanation, and the screening's own result is
 * unaffected by it.
 *
 * <p>{@code suggestedQuestions} is computed per response rather than stored, so it always reflects
 * the screening's current state.
 */
@JsonPropertyOrder({
    "id",
    "role",
    "content",
    "answer_state",
    "citations",
    "provider",
    "created_at",
    "suggested_questions"
})
public record ScreeningChatMessageRead(
        @JsonProperty("id") UUID id,
        @JsonProperty("role") String role,
        @JsonProperty("content") String content,
        @JsonProperty("answer_state") String answerState,
        @JsonProperty("citations") List<ScreeningChatCitationRead> citations,
        @JsonProperty("provider") ScreeningChatProviderRead provider,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("suggested_questions") List<String> suggestedQuestions) {

    public ScreeningChatMessageRead {
        citations = citations == null ? List.of() : List.copyOf(citations);
        suggestedQuestions = suggestedQuestions == null ? List.of() : List.copyOf(suggestedQuestions);
    }
}
