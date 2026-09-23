package com.trialsync.backend.research.rag.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.entity.EligibilityRagResult;
import com.trialsync.backend.entity.EligibilityRagRun;
import com.trialsync.backend.repository.EligibilityRagResultRepository;
import com.trialsync.backend.repository.EligibilityRagRunRepository;
import com.trialsync.backend.research.rag.GeminiChatModelHolder;
import com.trialsync.backend.research.rag.ResearchRagProperties;
import com.trialsync.backend.research.rag.dto.CriterionExplanationDto;
import com.trialsync.backend.research.rag.dto.RetrievedCriterionDto;
import com.trialsync.backend.research.rag.dto.TrialCriteriaExplainRequest;
import com.trialsync.backend.research.rag.dto.TrialCriteriaExplainResponse;
import com.trialsync.backend.research.rag.dto.TrialCriteriaRetrieveResponse;

import dev.langchain4j.model.chat.ChatLanguageModel;

/**
 * Service orchestrating LangChain4j scoped retrieval, Gemini structured explanation generation,
 * strict citation/provenance validation, and persistence of RAG runs and results.
 */
@Service
public class TrialCriteriaExplanationService {

    private static final Logger log = LoggerFactory.getLogger(TrialCriteriaExplanationService.class);

    private final TrialCriteriaRetriever retriever;
    private final CitationProvenanceValidator provenanceValidator;
    private final GeminiChatModelHolder geminiHolder;
    private final EligibilityRagRunRepository runRepository;
    private final EligibilityRagResultRepository resultRepository;
    private final ResearchRagProperties properties;
    private final ObjectMapper objectMapper;

    public TrialCriteriaExplanationService(
            TrialCriteriaRetriever retriever,
            CitationProvenanceValidator provenanceValidator,
            GeminiChatModelHolder geminiHolder,
            EligibilityRagRunRepository runRepository,
            EligibilityRagResultRepository resultRepository,
            ResearchRagProperties properties,
            ObjectMapper objectMapper) {
        this.retriever = retriever;
        this.provenanceValidator = provenanceValidator;
        this.geminiHolder = geminiHolder;
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TrialCriteriaExplainResponse explain(UUID versionId, TrialCriteriaExplainRequest request, UUID ownerId) {
        long startTime = System.currentTimeMillis();

        // 1. Scoped retrieval from LangChain4j vector store
        TrialCriteriaRetrieveResponse retrieveResponse = retriever.retrieve(
                versionId,
                request.query(),
                request.topK()
        );

        List<RetrievedCriterionDto> retrievedCriteria = retrieveResponse.criteria();

        // 2. Handle empty retrieval
        if (retrievedCriteria.isEmpty()) {
            EligibilityRagRun emptyRun = new EligibilityRagRun(versionId, request.query(), "EXPLAIN");
            emptyRun.setOwnerId(ownerId);
            emptyRun.setModel("none");
            emptyRun.setStatus("EMPTY_RETRIEVAL");
            emptyRun.setInsufficientEvidence(true);
            emptyRun.setSummaryText("No matching eligibility criteria were found in this approved trial version for the query.");
            emptyRun.setLatencyMs(System.currentTimeMillis() - startTime);
            runRepository.save(emptyRun);

            return new TrialCriteriaExplainResponse(
                    emptyRun.getId(),
                    versionId,
                    request.query(),
                    "none",
                    "EMPTY_RETRIEVAL",
                    true,
                    "No matching eligibility criteria were found for the query in this trial version.",
                    List.of(),
                    true,
                    TrialCriteriaExplainResponse.DEFAULT_DISCLAIMER
            );
        }

        // 3. Verify Gemini availability
        if (!geminiHolder.isAvailable()) {
            throw new ApplicationError(
                    "GEMINI_UNAVAILABLE",
                    "Gemini service is unavailable or unconfigured. Please check GEMINI_API_KEY.",
                    503);
        }

        ChatLanguageModel chatModel = geminiHolder.getModel();

        // 4. Construct grounded prompt with strict negative constraints
        String prompt = buildGroundedPrompt(request.query(), request.patientContext(), retrievedCriteria);

        String rawLlmResponse;
        try {
            rawLlmResponse = chatModel.generate(prompt);
        } catch (Exception e) {
            log.error("Gemini invocation failed: {}", e.getMessage(), e);
            throw new ApplicationError("GEMINI_CALL_FAILED", "Gemini explanation generation failed: " + e.getMessage(), 502);
        }

        // 5. Parse Gemini response and extract explanations
        ParsedGeminiResponse parsed = parseLlmResponse(rawLlmResponse, retrievedCriteria);

        // 6. Validate provenance and citations
        CitationProvenanceValidator.ValidationOutcome validationOutcome =
                provenanceValidator.validate(retrievedCriteria, parsed.explanations());

        boolean isProvenanceValid = validationOutcome.isValid();
        List<CriterionExplanationDto> validatedExplanations = validationOutcome.validatedExplanations();

        String runStatus = isProvenanceValid ? "COMPLETED" : "INSUFFICIENT_EVIDENCE";
        boolean insufficientEvidence = !isProvenanceValid;
        String finalSummary = isProvenanceValid
                ? parsed.summary()
                : "INSUFFICIENT EVIDENCE: " + validationOutcome.reason();

        long latency = System.currentTimeMillis() - startTime;

        // 7. Persist run and results
        EligibilityRagRun run = new EligibilityRagRun(versionId, request.query(), "EXPLAIN");
        run.setOwnerId(ownerId);
        run.setModel(properties.getGeminiModel());
        run.setStatus(runStatus);
        run.setInsufficientEvidence(insufficientEvidence);
        run.setSummaryText(finalSummary);
        run.setLatencyMs(latency);
        runRepository.save(run);

        int rank = 1;
        for (CriterionExplanationDto exp : validatedExplanations) {
            RetrievedCriterionDto matchingRetrieved = retrievedCriteria.stream()
                    .filter(rc -> rc.criterionId().equals(exp.criterionId()))
                    .findFirst()
                    .orElse(null);

            double score = (matchingRetrieved != null) ? matchingRetrieved.similarityScore() : 0.0;
            String sourceText = (matchingRetrieved != null) ? matchingRetrieved.sourceText() : exp.sourceText();
            String kind = (matchingRetrieved != null) ? matchingRetrieved.kind() : exp.kind();

            EligibilityRagResult result = new EligibilityRagResult(
                    run.getId(),
                    exp.criterionId(),
                    rank++,
                    score,
                    kind,
                    sourceText != null ? sourceText : "Unknown"
            );
            result.setExplanation(exp.explanation());
            result.setCitation(exp.citation());
            result.setProvenanceValid(exp.provenanceValid());
            resultRepository.save(result);
        }

        return new TrialCriteriaExplainResponse(
                run.getId(),
                versionId,
                request.query(),
                properties.getGeminiModel(),
                runStatus,
                insufficientEvidence,
                finalSummary,
                validatedExplanations,
                isProvenanceValid,
                TrialCriteriaExplainResponse.DEFAULT_DISCLAIMER
        );
    }

    private String buildGroundedPrompt(String query, String patientContext, List<RetrievedCriterionDto> criteria) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an eligibility criteria explanation assistant in TrialSync for clinical research.\n");
        sb.append("Your sole purpose is to explain and summarize the retrieved trial criteria below in relation to the query.\n\n");
        sb.append("CRITICAL BOUNDARIES & CONSTRAINTS:\n");
        sb.append("1. DO NOT make any final eligibility decision. You must NOT state that the patient is eligible, ineligible, or qualified.\n");
        sb.append("2. DO NOT override or replace the deterministic screening engine. Deterministic screening is the ONLY authority.\n");
        sb.append("3. DO NOT invent missing patient facts.\n");
        sb.append("4. DO NOT invent trial criteria not in the retrieved list.\n");
        sb.append("5. DO NOT fabricate citations. Every explanation item MUST cite the exact criterion_id provided in the retrieved list.\n\n");

        if (patientContext != null && !patientContext.isBlank()) {
            sb.append("PATIENT CONTEXT (FOR EXPLANATION ONLY):\n").append(patientContext).append("\n\n");
        }

        sb.append("USER QUERY:\n").append(query).append("\n\n");

        sb.append("RETRIEVED ELIGIBILITY CRITERIA:\n");
        for (RetrievedCriterionDto c : criteria) {
            sb.append(String.format("- criterion_id: %s\n  kind: %s\n  provenance: %s\n  text: %s\n\n",
                    c.criterionId(), c.kind(), c.provenance(), c.sourceText()));
        }

        sb.append("OUTPUT INSTRUCTIONS:\n");
        sb.append("Respond ONLY with a valid JSON object adhering to this schema:\n");
        sb.append("{\n");
        sb.append("  \"summary\": \"Concise objective overview of what these criteria require.\",\n");
        sb.append("  \"explanations\": [\n");
        sb.append("    {\n");
        sb.append("      \"criterion_id\": \"<UUID of the retrieved criterion>\",\n");
        sb.append("      \"citation\": \"TrialSync:Criterion:<UUID>\",\n");
        sb.append("      \"explanation\": \"Clear clinical explanation of what this criterion requires.\"\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");

        return sb.toString();
    }

    private record ParsedGeminiResponse(String summary, List<CriterionExplanationDto> explanations) {}

    private ParsedGeminiResponse parseLlmResponse(String rawResponse, List<RetrievedCriterionDto> retrievedCriteria) {
        try {
            // Strip markdown code fences if LLM wrapped with ```json ... ```
            String cleaned = rawResponse.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();

            JsonNode root = objectMapper.readTree(cleaned);
            String summary = root.has("summary") ? root.get("summary").asText() : "Criteria explanation";

            List<CriterionExplanationDto> list = new ArrayList<>();
            if (root.has("explanations") && root.get("explanations").isArray()) {
                for (JsonNode item : root.get("explanations")) {
                    String idStr = item.has("criterion_id") ? item.get("criterion_id").asText() : null;
                    UUID criterionId = null;
                    if (idStr != null) {
                        try {
                            criterionId = UUID.fromString(idStr);
                        } catch (Exception ignored) {
                        }
                    }
                    String citation = item.has("citation") ? item.get("citation").asText() : null;
                    String explanation = item.has("explanation") ? item.get("explanation").asText() : "";

                    // Match with retrieved
                    UUID finalId = criterionId;
                    RetrievedCriterionDto matching = retrievedCriteria.stream()
                            .filter(rc -> rc.criterionId().equals(finalId))
                            .findFirst()
                            .orElse(null);

                    String kind = matching != null ? matching.kind() : "UNKNOWN";
                    String sourceText = matching != null ? matching.sourceText() : "";

                    list.add(new CriterionExplanationDto(
                            criterionId,
                            kind,
                            sourceText,
                            citation,
                            explanation,
                            criterionId != null
                    ));
                }
            }

            if (list.isEmpty()) {
                // Fallback: build default explanations if json structure was empty
                for (RetrievedCriterionDto rc : retrievedCriteria) {
                    list.add(new CriterionExplanationDto(
                            rc.criterionId(),
                            rc.kind(),
                            rc.sourceText(),
                            rc.provenance(),
                            summary,
                            true
                    ));
                }
            }

            return new ParsedGeminiResponse(summary, list);
        } catch (Exception e) {
            log.warn("Failed to parse Gemini structured JSON: {}. Using raw fallback.", e.getMessage());
            // Fallback gracefully to raw explanation text
            List<CriterionExplanationDto> fallbackList = new ArrayList<>();
            for (RetrievedCriterionDto rc : retrievedCriteria) {
                fallbackList.add(new CriterionExplanationDto(
                        rc.criterionId(),
                        rc.kind(),
                        rc.sourceText(),
                        rc.provenance(),
                        rawResponse.length() > 500 ? rawResponse.substring(0, 500) : rawResponse,
                        true
                ));
            }
            return new ParsedGeminiResponse(rawResponse, fallbackList);
        }
    }
}
