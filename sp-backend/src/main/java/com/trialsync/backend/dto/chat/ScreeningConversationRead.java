package com.trialsync.backend.dto.chat;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The whole conversation for one screening: {@code schemas.ScreeningConversationRead}.
 *
 * <p>{@code maxMessages} and {@code maxMessageChars} are published so the client can enforce the
 * same limits the server does. The history is a rolling window: once it is full the oldest turns
 * are deleted, which is why this response is the authoritative view rather than an append-only log.
 */
@JsonPropertyOrder({
    "screening_id",
    "messages",
    "provider",
    "suggested_questions",
    "max_messages",
    "max_message_chars"
})
public record ScreeningConversationRead(
        @JsonProperty("screening_id") UUID screeningId,
        @JsonProperty("messages") List<ScreeningChatMessageRead> messages,
        @JsonProperty("provider") ScreeningChatProviderRead provider,
        @JsonProperty("suggested_questions") List<String> suggestedQuestions,
        @JsonProperty("max_messages") int maxMessages,
        @JsonProperty("max_message_chars") int maxMessageChars) {

    public ScreeningConversationRead {
        messages = messages == null ? List.of() : List.copyOf(messages);
        suggestedQuestions = suggestedQuestions == null ? List.of() : List.copyOf(suggestedQuestions);
    }
}
