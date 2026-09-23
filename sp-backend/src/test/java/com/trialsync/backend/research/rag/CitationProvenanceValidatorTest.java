package com.trialsync.backend.research.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.trialsync.backend.research.rag.dto.CriterionExplanationDto;
import com.trialsync.backend.research.rag.dto.RetrievedCriterionDto;
import com.trialsync.backend.research.rag.service.CitationProvenanceValidator;

class CitationProvenanceValidatorTest {

    private CitationProvenanceValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CitationProvenanceValidator();
    }

    @Test
    void testValidCitationsPass() {
        UUID criterion1 = UUID.randomUUID();
        UUID criterion2 = UUID.randomUUID();

        List<RetrievedCriterionDto> retrieved = List.of(
                new RetrievedCriterionDto(criterion1, "INCLUSION", 1, "Age >= 18", "TrialSync:Criterion:" + criterion1, 0.9, null),
                new RetrievedCriterionDto(criterion2, "EXCLUSION", 2, "Pregnant", "TrialSync:Criterion:" + criterion2, 0.85, null)
        );

        List<CriterionExplanationDto> explanations = List.of(
                new CriterionExplanationDto(criterion1, "INCLUSION", "Age >= 18", "TrialSync:Criterion:" + criterion1, "Patient must be adult", true),
                new CriterionExplanationDto(criterion2, "EXCLUSION", "Pregnant", "TrialSync:Criterion:" + criterion2, "Pregnancy is exclusionary", true)
        );

        CitationProvenanceValidator.ValidationOutcome outcome = validator.validate(retrieved, explanations);

        assertTrue(outcome.isValid());
        assertEquals(2, outcome.validatedExplanations().size());
        assertTrue(outcome.validatedExplanations().get(0).provenanceValid());
        assertTrue(outcome.validatedExplanations().get(1).provenanceValid());
    }

    @Test
    void testFabricatedCitationFails() {
        UUID retrievedId = UUID.randomUUID();
        UUID fabricatedId = UUID.randomUUID();

        List<RetrievedCriterionDto> retrieved = List.of(
                new RetrievedCriterionDto(retrievedId, "INCLUSION", 1, "Type 2 Diabetes", "TrialSync:Criterion:" + retrievedId, 0.9, null)
        );

        // LLM cites fabricatedId which was NEVER retrieved
        List<CriterionExplanationDto> explanations = List.of(
                new CriterionExplanationDto(fabricatedId, "INCLUSION", "Type 2 Diabetes", "TrialSync:Criterion:" + fabricatedId, "Requires diabetes diagnosis", true)
        );

        CitationProvenanceValidator.ValidationOutcome outcome = validator.validate(retrieved, explanations);

        assertFalse(outcome.isValid());
        assertTrue(outcome.reason().contains("Ungrounded or fabricated citation"));
        assertFalse(outcome.validatedExplanations().get(0).provenanceValid());
    }

    @Test
    void testMissingCitationFails() {
        UUID retrievedId = UUID.randomUUID();

        List<RetrievedCriterionDto> retrieved = List.of(
                new RetrievedCriterionDto(retrievedId, "INCLUSION", 1, "Type 2 Diabetes", "TrialSync:Criterion:" + retrievedId, 0.9, null)
        );

        List<CriterionExplanationDto> explanations = List.of(
                new CriterionExplanationDto(null, "INCLUSION", "Type 2 Diabetes", null, "Requires diabetes diagnosis", true)
        );

        CitationProvenanceValidator.ValidationOutcome outcome = validator.validate(retrieved, explanations);

        assertFalse(outcome.isValid());
        assertFalse(outcome.validatedExplanations().get(0).provenanceValid());
    }
}
