package com.trialsync.backend.terminology;

import java.util.List;

import com.trialsync.backend.dto.concept.TerminologySuggestion;

/**
 * The outcome of a terminology lookup: {@code TerminologySuggestionResult}.
 *
 * <p>The two lists are independent. An empty {@code suggestions} with a populated
 * {@code unavailableSources} means the lookup could not run; an empty {@code suggestions} with an
 * empty {@code unavailableSources} means it ran and matched nothing. The distinction is what lets
 * the API tell an administrator "no match" apart from "not asked", instead of guessing a code.
 */
public record TerminologySuggestionResult(
        List<TerminologySuggestion> suggestions, List<String> unavailableSources) {

    public TerminologySuggestionResult {
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        unavailableSources = unavailableSources == null ? List.of() : List.copyOf(unavailableSources);
    }

    /** A lookup that could not run, carrying the reason to show the administrator. */
    public static TerminologySuggestionResult unavailable(String reason) {
        return new TerminologySuggestionResult(List.of(), List.of(reason));
    }
}
