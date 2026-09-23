package com.trialsync.backend.controller;

import com.trialsync.backend.dto.chat.ScreeningChatMessageCreate;
import com.trialsync.backend.dto.chat.ScreeningChatMessageRead;
import com.trialsync.backend.dto.chat.ScreeningConversationRead;
import com.trialsync.backend.service.ChatService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Port of the conversation routes of {@code trialsync.api.screenings}.
 *
 * <p>The conversation hangs off a screening rather than standing on its own, which is why the paths
 * are nested and why authorisation is the screening's: a caller who cannot read the result cannot
 * read or extend the discussion of it.
 *
 * <p>These routes bind and delegate. The refusal policy, the provider call, the citation provenance
 * check and the transaction boundaries all live in {@link ChatService}, and nothing reachable from
 * here can alter an eligibility verdict.
 */
@RestController
@RequestMapping("/api/v1/screenings/{screeningId}/conversation")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** {@code GET /api/v1/screenings/{screening_id}/conversation}. */
    @GetMapping
    public ScreeningConversationRead getScreeningConversation(@PathVariable UUID screeningId) {
        return chatService.getConversation(screeningId);
    }

    /**
     * {@code POST /api/v1/screenings/{screening_id}/conversation/messages} - stores the question
     * and the answer, and returns the answer.
     */
    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ScreeningChatMessageRead createScreeningChatMessage(
            @PathVariable UUID screeningId,
            @Valid @RequestBody ScreeningChatMessageCreate payload) {
        return chatService.createMessage(screeningId, payload);
    }

    /**
     * {@code DELETE /api/v1/screenings/{screening_id}/conversation} - deletes every turn for this
     * screening. The screening itself is not touched.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearScreeningConversation(@PathVariable UUID screeningId) {
        chatService.clearConversation(screeningId);
    }
}
