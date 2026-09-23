package com.trialsync.backend.nlp;

/**
 * Port of {@code trialsync.nlp.groq.ProviderCallError}.
 *
 * <p>Carries the provider-neutral failure code the callers switch on
 * ({@code PROVIDER_TIMEOUT}, {@code PROVIDER_RATE_LIMITED}, {@code PROVIDER_RESPONSE_INVALID},
 * {@code PROVIDER_ERROR}, {@code PROVIDER_INPUT_TOO_LARGE}, {@code ASSISTANT_DISABLED}). The API
 * layer maps those codes onto the public {@code ASSISTANT_*} error codes; the raw provider code
 * never reaches a client.
 *
 * <p>No provider payload, prompt or patient text is ever attached to this exception - the message is
 * one of a fixed set of strings copied verbatim from the Python module.
 */
public class ProviderCallException extends RuntimeException {

    private final String code;

    public ProviderCallException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
