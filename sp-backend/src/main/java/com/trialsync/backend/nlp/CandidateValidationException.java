package com.trialsync.backend.nlp;

/**
 * Raised when a candidate document fails the import contract.
 *
 * <p>Stands in for Pydantic's {@code ValidationError} at the point {@code _convert_payload} calls
 * {@code model_validate}. {@link GroqExtractor} catches it and converts it into the neutral
 * {@code PROVIDER_RESPONSE_INVALID} error, so a malformed model response never reaches a reviewer
 * and never explains itself to a caller.
 */
public class CandidateValidationException extends RuntimeException {

    public CandidateValidationException(String message) {
        super(message);
    }

    public CandidateValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
