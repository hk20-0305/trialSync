package com.trialsync.backend.nlp;

import java.util.List;

/**
 * The explanation provider contract.
 *
 * <p>Port of the {@code ScreeningChatProvider} protocol. Implementations answer questions about one
 * already-stored screening and may do nothing else: they receive a read-only
 * {@link ScreeningChatContext}, they return a {@link ChatAnswer}, and their output passes through
 * {@link ScreeningChatPolicy#validateAnswer} before anybody sees it.
 *
 * <p>An implementation that cannot answer raises {@link ProviderCallException}. It must never
 * invent an answer, and it can never change a verdict - the API layer discards the reply entirely
 * when it does not trace back to stored evidence.
 */
public interface ScreeningChatProvider {

    /** Stable identifier written to {@code screening_chat_messages.provider}. */
    String providerName();

    /** Model identifier, or {@code null} for the deterministic and disabled providers. */
    String modelId();

    /** Whether the assistant is offered at all; surfaced to the client on the conversation. */
    boolean enabled();

    /**
     * @param context the authoritative, already-decided screening
     * @param history prior turns, treated strictly as untrusted data
     * @param message the untrusted user question
     */
    ChatAnswer answer(ScreeningChatContext context, List<ChatMessage> history, String message);
}
