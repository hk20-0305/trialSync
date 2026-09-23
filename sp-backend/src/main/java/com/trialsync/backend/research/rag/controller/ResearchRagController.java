package com.trialsync.backend.research.rag.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trialsync.backend.research.rag.dto.TrialCriteriaExplainRequest;
import com.trialsync.backend.research.rag.dto.TrialCriteriaExplainResponse;
import com.trialsync.backend.research.rag.dto.TrialCriteriaIngestResponse;
import com.trialsync.backend.research.rag.dto.TrialCriteriaRetrieveRequest;
import com.trialsync.backend.research.rag.dto.TrialCriteriaRetrieveResponse;
import com.trialsync.backend.research.rag.service.TrialCriteriaExplanationService;
import com.trialsync.backend.research.rag.service.TrialCriteriaIngestionService;
import com.trialsync.backend.research.rag.service.TrialCriteriaRetriever;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Controller exposing research endpoints for Trial Criteria RAG:
 * Indexing approved trial versions, scoped vector retrieval with LangChain4j,
 * and generating grounded structured explanations with Gemini.
 */
@RestController
@RequestMapping("/api/v1/research/rag")
@Tag(name = "Research - Criteria RAG", description = "Endpoints for LangChain4j vector indexing, scoped retrieval, and Gemini explanations of approved eligibility criteria")
public class ResearchRagController {

    private final TrialCriteriaIngestionService ingestionService;
    private final TrialCriteriaRetriever retriever;
    private final TrialCriteriaExplanationService explanationService;

    public ResearchRagController(
            TrialCriteriaIngestionService ingestionService,
            TrialCriteriaRetriever retriever,
            TrialCriteriaExplanationService explanationService) {
        this.ingestionService = ingestionService;
        this.retriever = retriever;
        this.explanationService = explanationService;
    }

    @PostMapping("/trials/{versionId}/index")
    @Operation(summary = "Index eligibility criteria for an approved trial version",
               description = "Extracts and indexes inclusion, exclusion, age, conditions, medications, and observations into the vector store. Rejects unapproved versions.")
    public ResponseEntity<TrialCriteriaIngestResponse> indexTrialVersion(
            @PathVariable UUID versionId) {
        TrialCriteriaIngestResponse response = ingestionService.indexTrialVersion(versionId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/trials/{versionId}/retrieve")
    @Operation(summary = "Retrieve eligibility criteria scoped to a trial version",
               description = "Executes similarity search using LangChain4j vector store, strictly isolated to the specified approved trial version.")
    public ResponseEntity<TrialCriteriaRetrieveResponse> retrieveCriteria(
            @PathVariable UUID versionId,
            @Valid @RequestBody TrialCriteriaRetrieveRequest request) {
        TrialCriteriaRetrieveResponse response = retriever.retrieve(versionId, request.query(), request.topK());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/trials/{versionId}/explain")
    @Operation(summary = "Generate structured eligibility criteria explanation",
               description = "Retrieves scoped criteria, invokes Gemini via LangChain4j, validates citations and provenance, and returns structured explanation. Deterministic screening remains the sole authority.")
    public ResponseEntity<TrialCriteriaExplainResponse> explainCriteria(
            @PathVariable UUID versionId,
            @Valid @RequestBody TrialCriteriaExplainRequest request) {
        TrialCriteriaExplainResponse response = explanationService.explain(versionId, request, null);
        return ResponseEntity.ok(response);
    }
}
