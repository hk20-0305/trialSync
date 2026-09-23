package com.trialsync.backend.nlp;

/**
 * Supplies the chat provider in force for the current request.
 *
 * <p>The seam that replaces Python's {@code request.app.state.chat_provider} lookup, so
 * {@code ChatService} never caches a provider and a swapped provider takes effect immediately.
 */
@FunctionalInterface
public interface ChatServiceProviderSource {

    ScreeningChatProvider get();
}
