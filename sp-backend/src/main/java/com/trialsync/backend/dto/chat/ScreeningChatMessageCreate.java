package com.trialsync.backend.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body of {@code POST /api/v1/screenings/{id}/conversation/messages}:
 * {@code schemas.ScreeningChatMessageCreate}.
 *
 * <p>Two limits apply, and both are Python's. This one is the absolute contract bound - 1 to 4,000
 * characters - and a breach is a request-validation failure. The configured
 * {@code TRIALSYNC_SCREENING_CHAT_MESSAGE_MAX_CHARS} is checked afterwards in the service and
 * reports {@code ASSISTANT_MESSAGE_TOO_LONG} instead, because that one is a deployment policy rather
 * than a malformed request.
 */
public record ScreeningChatMessageCreate(
        @JsonProperty("message") @NotNull @Size(min = 1, max = 4_000) String message) {}
