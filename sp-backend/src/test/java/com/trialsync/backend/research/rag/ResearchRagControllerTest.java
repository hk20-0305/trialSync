package com.trialsync.backend.research.rag;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.config.GlobalExceptionHandler;
import com.trialsync.backend.research.rag.controller.ResearchRagController;
import com.trialsync.backend.research.rag.dto.RetrievedCriterionDto;
import com.trialsync.backend.research.rag.dto.TrialCriteriaExplainRequest;
import com.trialsync.backend.research.rag.dto.TrialCriteriaExplainResponse;
import com.trialsync.backend.research.rag.dto.TrialCriteriaIngestResponse;
import com.trialsync.backend.research.rag.dto.TrialCriteriaRetrieveRequest;
import com.trialsync.backend.research.rag.dto.TrialCriteriaRetrieveResponse;
import com.trialsync.backend.research.rag.service.TrialCriteriaExplanationService;
import com.trialsync.backend.research.rag.service.TrialCriteriaIngestionService;
import com.trialsync.backend.research.rag.service.TrialCriteriaRetriever;

import static org.mockito.Mockito.mock;

class ResearchRagControllerTest {

    private MockMvc mockMvc;
    private TrialCriteriaIngestionService ingestionService;
    private TrialCriteriaRetriever retriever;
    private TrialCriteriaExplanationService explanationService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ingestionService = mock(TrialCriteriaIngestionService.class);
        retriever = mock(TrialCriteriaRetriever.class);
        explanationService = mock(TrialCriteriaExplanationService.class);
        objectMapper = new ObjectMapper();

        ResearchRagController controller = new ResearchRagController(
                ingestionService,
                retriever,
                explanationService
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void testIndexApprovedTrialEndpoint() throws Exception {
        UUID versionId = UUID.randomUUID();
        TrialCriteriaIngestResponse response = new TrialCriteriaIngestResponse(
                versionId,
                "INDEXED",
                4,
                "sha256checksum",
                OffsetDateTime.now(),
                "Successfully indexed 4 eligibility criteria"
        );

        when(ingestionService.indexTrialVersion(versionId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/research/rag/trials/" + versionId + "/index"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INDEXED"))
                .andExpect(jsonPath("$.chunk_count").value(4));
    }

    @Test
    void testIndexRejectsUnapprovedTrial() throws Exception {
        UUID versionId = UUID.randomUUID();
        when(ingestionService.indexTrialVersion(versionId))
                .thenThrow(ApplicationError.unprocessable("UNAPPROVED_TRIAL_VERSION", "Only approved trial versions can be indexed in RAG", "status"));

        mockMvc.perform(post("/api/v1/research/rag/trials/" + versionId + "/index"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("UNAPPROVED_TRIAL_VERSION"));
    }

    @Test
    void testRetrieveCriteriaEndpoint() throws Exception {
        UUID versionId = UUID.randomUUID();
        UUID criterionId = UUID.randomUUID();
        TrialCriteriaRetrieveRequest request = new TrialCriteriaRetrieveRequest("HbA1c", 5);
        TrialCriteriaRetrieveResponse response = new TrialCriteriaRetrieveResponse(
                versionId,
                "HbA1c",
                1,
                List.of(new RetrievedCriterionDto(criterionId, "INCLUSION", 1, "HbA1c 7-10%", "TrialSync:Criterion:" + criterionId, 0.9, null))
        );

        when(retriever.retrieve(eq(versionId), eq("HbA1c"), eq(5))).thenReturn(response);

        mockMvc.perform(post("/api/v1/research/rag/trials/" + versionId + "/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results_count").value(1))
                .andExpect(jsonPath("$.criteria[0].kind").value("INCLUSION"));
    }

    @Test
    void testExplainCriteriaEndpoint() throws Exception {
        UUID versionId = UUID.randomUUID();
        TrialCriteriaExplainRequest request = new TrialCriteriaExplainRequest("HbA1c requirements", "Patient HbA1c 8.5", 5);
        TrialCriteriaExplainResponse response = new TrialCriteriaExplainResponse(
                UUID.randomUUID(),
                versionId,
                "HbA1c requirements",
                "gemini-1.5-flash",
                "COMPLETED",
                false,
                "Patient meets the required range.",
                List.of(),
                true,
                TrialCriteriaExplainResponse.DEFAULT_DISCLAIMER
        );

        when(explanationService.explain(eq(versionId), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/research/rag/trials/" + versionId + "/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.disclaimer").exists());
    }

    @Test
    void testExplainWhenGeminiUnavailable() throws Exception {
        UUID versionId = UUID.randomUUID();
        TrialCriteriaExplainRequest request = new TrialCriteriaExplainRequest("diabetes", null, 5);

        when(explanationService.explain(eq(versionId), any(), any()))
                .thenThrow(new ApplicationError("GEMINI_UNAVAILABLE", "Gemini service is unavailable or unconfigured", 503));

        mockMvc.perform(post("/api/v1/research/rag/trials/" + versionId + "/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("GEMINI_UNAVAILABLE"));
    }
}
