package com.trialsync.backend.imports;

import java.util.List;
import java.util.Map;

import com.trialsync.backend.nlp.CandidateValidationException;

/**
 * A candidate document that does not satisfy the import contract, carrying the field-level errors
 * that explain why.
 *
 * <p>It extends {@link CandidateValidationException} so that {@code GroqExtractor}, which catches
 * that type and converts it into the neutral {@code PROVIDER_RESPONSE_INVALID}, keeps working
 * unchanged - a provider's mistakes are never described back to a caller. The details are for the
 * other caller: when a <em>reviewer</em> submits candidates the import API turns them into the
 * {@code details} array of an {@code IMPORT_REVIEW_INVALID} response, so their client can point at
 * the offending field.
 *
 * <p>The same distinction exists in Python, where {@code _validate_candidates} catches the
 * {@code ValidationError} and forwards {@code exception.errors(include_url=False)}, while
 * {@code _convert_payload} discards it.
 */
public class ImportCandidateValidationException extends CandidateValidationException {

    private final List<Map<String, Object>> details;

    public ImportCandidateValidationException(List<Map<String, Object>> details) {
        super("candidate validation failed with " + details.size() + " error(s)");
        this.details = List.copyOf(details);
    }

    public List<Map<String, Object>> getDetails() {
        return details;
    }
}
