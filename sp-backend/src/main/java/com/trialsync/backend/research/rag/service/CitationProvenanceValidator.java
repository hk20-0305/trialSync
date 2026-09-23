package com.trialsync.backend.research.rag.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.trialsync.backend.research.rag.dto.CriterionExplanationDto;
import com.trialsync.backend.research.rag.dto.RetrievedCriterionDto;

/**
 * Validates that all LLM-generated explanations and citations strictly ground in the retrieved criteria.
 * Any fabricated citation, ungrounded claim, or missing provenance marks the result as invalid.
 */
@Component
public class CitationProvenanceValidator {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"
    );

    public record ValidationOutcome(
            boolean isValid,
            String reason,
            List<CriterionExplanationDto> validatedExplanations
    ) {}

    public ValidationOutcome validate(
            List<RetrievedCriterionDto> retrievedCriteria,
            List<CriterionExplanationDto> explanations) {

        if (retrievedCriteria.isEmpty()) {
            return new ValidationOutcome(true, "No criteria were retrieved to validate", List.of());
        }

        if (explanations == null || explanations.isEmpty()) {
            return new ValidationOutcome(false, "LLM returned no criterion explanations for retrieved criteria", List.of());
        }

        Set<UUID> validCriterionIds = new HashSet<>();
        for (RetrievedCriterionDto r : retrievedCriteria) {
            validCriterionIds.add(r.criterionId());
        }

        List<CriterionExplanationDto> adjustedList = new ArrayList<>();
        boolean allValid = true;
        StringBuilder errorDetails = new StringBuilder();

        for (CriterionExplanationDto exp : explanations) {
            UUID citedId = exp.criterionId();
            boolean citationMatches = false;

            // 1. Verify criterionId exists in retrieved candidate set
            if (citedId != null && validCriterionIds.contains(citedId)) {
                citationMatches = true;
            } else if (exp.citation() != null) {
                // Check if citation text contains a valid retrieved UUID
                Matcher matcher = UUID_PATTERN.matcher(exp.citation());
                while (matcher.find()) {
                    try {
                        UUID foundUuid = UUID.fromString(matcher.group(1));
                        if (validCriterionIds.contains(foundUuid)) {
                            citationMatches = true;
                            citedId = foundUuid;
                            break;
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            if (!citationMatches) {
                allValid = false;
                errorDetails.append(String.format("Ungrounded or fabricated citation: id=%s, citation=%s. ",
                        exp.criterionId(), exp.citation()));
                adjustedList.add(new CriterionExplanationDto(
                        exp.criterionId(),
                        exp.kind(),
                        exp.sourceText(),
                        exp.citation(),
                        exp.explanation() + " [UNVERIFIED: Citation is not grounded in retrieved trial criteria]",
                        false
                ));
            } else {
                adjustedList.add(new CriterionExplanationDto(
                        citedId,
                        exp.kind(),
                        exp.sourceText(),
                        exp.citation() != null ? exp.citation() : "TrialSync:Criterion:" + citedId,
                        exp.explanation(),
                        true
                ));
            }
        }

        if (!allValid) {
            return new ValidationOutcome(
                    false,
                    "Provenance check failed: " + errorDetails.toString().trim(),
                    adjustedList
            );
        }

        return new ValidationOutcome(true, "All citations successfully verified against retrieved criteria", adjustedList);
    }
}
