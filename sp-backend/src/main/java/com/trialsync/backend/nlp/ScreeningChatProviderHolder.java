package com.trialsync.backend.nlp;

/**
 * Holds the chat provider the service currently answers with.
 *
 * <p>Python kept the provider on {@code app.state.chat_provider}, which the API read on every
 * request and the test suite swapped between cases. A plain injected bean cannot be swapped, so the
 * indirection is preserved here: {@link ChatServiceProviderSource#get()} is read per request, and
 * tests replace it with {@link #set}.
 *
 * <p>Swapping a provider changes only how an explanation is worded. It cannot change a stored
 * screening result, because every answer still has to survive
 * {@link ScreeningChatPolicy#validateAnswer} against the same stored evidence.
 */
public class ScreeningChatProviderHolder implements ChatServiceProviderSource {

    private volatile ScreeningChatProvider provider;

    public ScreeningChatProviderHolder(ScreeningChatProvider provider) {
        this.provider = provider;
    }

    @Override
    public ScreeningChatProvider get() {
        return provider;
    }

    /** Replaces the active provider. Intended for tests and for runtime reconfiguration. */
    public void set(ScreeningChatProvider replacement) {
        this.provider = replacement;
    }
}
