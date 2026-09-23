package com.trialsync.backend.research.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.entity.EligibilityRagResult;
import com.trialsync.backend.entity.EligibilityRagRun;
import com.trialsync.backend.repository.EligibilityRagResultRepository;
import com.trialsync.backend.repository.EligibilityRagRunRepository;
import com.trialsync.backend.research.rag.dto.RetrievedCriterionDto;
import com.trialsync.backend.research.rag.dto.TrialCriteriaExplainRequest;
import com.trialsync.backend.research.rag.dto.TrialCriteriaExplainResponse;
import com.trialsync.backend.research.rag.dto.TrialCriteriaRetrieveResponse;
import com.trialsync.backend.research.rag.service.CitationProvenanceValidator;
import com.trialsync.backend.research.rag.service.TrialCriteriaExplanationService;
import com.trialsync.backend.research.rag.service.TrialCriteriaRetriever;

import dev.langchain4j.model.chat.ChatLanguageModel;

class TrialCriteriaExplanationServiceTest {

    private TrialCriteriaRetriever retriever;
    private CitationProvenanceValidator validator;
    private GeminiChatModelHolder geminiHolder;
    private EligibilityRagRunRepository runRepository;
    private EligibilityRagResultRepository resultRepository;
    private ResearchRagProperties properties;
    private ObjectMapper objectMapper;
    private ChatLanguageModel mockChatModel;

    private TrialCriteriaExplanationService explanationService;

    @BeforeEach
    void setUp() {
        retriever = mock(TrialCriteriaRetriever.class);
        validator = new CitationProvenanceValidator();
        runRepository = mock(EligibilityRagRunRepository.class);
        resultRepository = mock(EligibilityRagResultRepository.class);
        properties = new ResearchRagProperties();
        properties.setGeminiModel("gemini-1.5-flash");
        objectMapper = new ObjectMapper();

        mockChatModel = mock(ChatLanguageModel.class);
        geminiHolder = new GeminiChatModelHolder(mockChatModel, true);

        explanationService = new TrialCriteriaExplanationService(
                retriever,
                validator,
                geminiHolder,
                runRepository,
                resultRepository,
                properties,
                objectMapper
        );
    }

    @Test
    void testSuccessfulExplanationWithValidProvenance() {
        UUID versionId = UUID.randomUUID();
        UUID criterionId = UUID.randomUUID();

        RetrievedCriterionDto retrieved = new RetrievedCriterionDto(
                criterionId,
                "INCLUSION",
                1,
                "HbA1c between 7.0% and 10.0%",
                "TrialSync:Criterion:" + criterionId,
                0.92,
                "{\"fact\":\"observation.hba1c\"}"
        );

        when(retriever.retrieve(any(), any(), any()))
                .thenReturn(new TrialCriteriaRetrieveResponse(versionId, "HbA1c", 1, List.of(retrieved)));

        String geminiJson = String.format("""
                {
                  "summary": "The trial requires patients to have moderate glycemic control.",
                  "explanations": [
                    {
                      "criterion_id": "%s",
                      "citation": "TrialSync:Criterion:%s",
                      "explanation": "Patient HbA1c lab measurement must be within 7.0%% to 10.0%%."
                    }
                  ]
                }
                """, criterionId, criterionId);

        when(mockChatModel.generate(anyString())).thenReturn(geminiJson);

        TrialCriteriaExplainRequest request = new TrialCriteriaExplainRequest("What are the HbA1c requirements?", "HbA1c is 8.0%", 5);
        TrialCriteriaExplainResponse response = explanationService.explain(versionId, request, null);

        assertNotNull(response);
        assertEquals("COMPLETED", response.status());
        assertFalse(response.insufficientEvidence());
        assertTrue(response.provenanceValid());
        assertEquals(1, response.explanations().size());
        assertTrue(response.explanations().get(0).provenanceValid());
        assertNotNull(response.disclaimer());
        assertTrue(response.disclaimer().contains("screening engine remains the ONLY authority"));

        verify(runRepository).save(any(EligibilityRagRun.class));
        verify(resultRepository).save(any(EligibilityRagResult.class));
    }

    @Test
    void testEmptyRetrievalReturnsInsufficientEvidenceWithoutCallingGemini() {
        UUID versionId = UUID.randomUUID();

        when(retriever.retrieve(any(), any(), any()))
                .thenReturn(new TrialCriteriaRetrieveResponse(versionId, "unrelated query", 0, List.of()));

        TrialCriteriaExplainRequest request = new TrialCriteriaExplainRequest("unrelated query", null, 5);
        TrialCriteriaExplainResponse response = explanationService.explain(versionId, request, null);

        assertNotNull(response);
        assertEquals("EMPTY_RETRIEVAL", response.status());
        assertTrue(response.insufficientEvidence());
        assertTrue(response.explanations().isEmpty());

        // Crucial invariant: Gemini is NEVER called on empty retrieval
        verify(mockChatModel, never()).generate(anyString());
        verify(runRepository).save(any(EligibilityRagRun.class));
    }

    @Test
    void testGeminiUnavailableThrows503() {
        UUID versionId = UUID.randomUUID();
        UUID criterionId = UUID.randomUUID();

        geminiHolder.setUnavailable();

        when(retriever.retrieve(any(), any(), any()))
                .thenReturn(new TrialCriteriaRetrieveResponse(versionId, "diabetes", 1, List.of(
                        new RetrievedCriterionDto(criterionId, "INCLUSION", 1, "Type 2 Diabetes", "TrialSync:Criterion:" + criterionId, 0.9, null)
                )));

        TrialCriteriaExplainRequest request = new TrialCriteriaExplainRequest("diabetes", null, 5);
        ApplicationError error = assertThrows(ApplicationError.class,
                () -> explanationService.explain(versionId, request, null));

        assertEquals(503, error.getStatusCode());
        assertEquals("GEMINI_UNAVAILABLE", error.getCode());
    }

    @Test
    void testFabricatedCitationReturnsInsufficientEvidence() {
        UUID versionId = UUID.randomUUID();
        UUID retrievedCriterionId = UUID.randomUUID();
        UUID fabricatedCriterionId = UUID.randomUUID();

        RetrievedCriterionDto retrieved = new RetrievedCriterionDto(
                retrievedCriterionId,
                "INCLUSION",
                1,
                "Type 2 Diabetes Mellitus",
                "TrialSync:Criterion:" + retrievedCriterionId,
                0.88,
                null
        );

        when(retriever.retrieve(any(), any(), any()))
                .thenReturn(new TrialCriteriaRetrieveResponse(versionId, "diabetes", 1, List.of(retrieved)));

        // Gemini returns fabricated criterion UUID
        String geminiJson = String.format("""
                {
                  "summary": "Requires diabetes condition.",
                  "explanations": [
                    {
                      "criterion_id": "%s",
                      "citation": "TrialSync:Criterion:%s",
                      "explanation": "Fabricated criterion cited."
                    }
                  ]
                }
                """, fabricatedCriterionId, fabricatedCriterionId);

        when(mockChatModel.generate(anyString())).thenReturn(geminiJson);

        TrialCriteriaExplainRequest request = new TrialCriteriaExplainRequest("diabetes", null, 5);
        TrialCriteriaExplainResponse response = explanationService.explain(versionId, request, null);

        assertNotNull(response);
        assertEquals("INSUFFICIENT_EVIDENCE", response.status());
        assertTrue(response.insufficientEvidence());
        assertFalse(response.provenanceValid());
        assertTrue(response.summary().contains("INSUFFICIENT EVIDENCE"));
    }

    @Test
    void testGeminiFailureHandling() {
        UUID versionId = UUID.randomUUID();
        UUID criterionId = UUID.randomUUID();

        when(retriever.retrieve(any(), any(), any()))
                .thenReturn(new TrialCriteriaRetrieveResponse(versionId, "diabetes", 1, List.of(
                        new RetrievedCriterionDto(criterionId, "INCLUSION", 1, "Diabetes", "TrialSync:Criterion:" + criterionId, 0.9, null)
                )));

        when(mockChatModel.generate(anyString())).thenThrow(new RuntimeException("Gemini quota exceeded"));

        TrialCriteriaExplainRequest request = new TrialCriteriaExplainRequest("diabetes", null, 5);
        ApplicationError error = assertThrows(ApplicationError.class,
                () -> explanationService.explain(versionId, request, null));

        assertEquals(502, error.getStatusCode());
        assertEquals("GEMINI_CALL_FAILED", error.getCode());
    }

    @Test
    void testDeterministicScreeningBoundaryUnchanged() {
        // Invariant check: Verify that explanation disclaimer is present and explicit
        TrialCriteriaExplainResponse response = new TrialCriteriaExplainResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "query",
                "gemini-1.5-flash",
                "COMPLETED",
                false,
                "Summary",
                List.of(),
                true,
                TrialCriteriaExplainResponse.DEFAULT_DISCLAIMER
        );

        assertTrue(response.disclaimer().contains("deterministic screening engine remains the ONLY authority"));
        assertTrue(response.disclaimer().contains("cannot alter patient eligibility"));
    }
}
