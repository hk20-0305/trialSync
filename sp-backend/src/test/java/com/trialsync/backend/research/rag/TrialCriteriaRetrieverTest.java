package com.trialsync.backend.research.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.domain.model.CriterionKind;
import com.trialsync.backend.entity.Criterion;
import com.trialsync.backend.entity.TrialVersion;
import com.trialsync.backend.entity.enums.VersionStatus;
import com.trialsync.backend.repository.EligibilityRagIndexRepository;
import com.trialsync.backend.repository.TrialVersionRepository;
import com.trialsync.backend.research.rag.dto.RetrievedCriterionDto;
import com.trialsync.backend.research.rag.dto.TrialCriteriaRetrieveResponse;
import com.trialsync.backend.research.rag.service.TrialCriteriaIngestionService;
import com.trialsync.backend.research.rag.service.TrialCriteriaRetriever;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

class TrialCriteriaRetrieverTest {

    private TrialVersionRepository trialVersionRepository;
    private EligibilityRagIndexRepository ragIndexRepository;
    private InMemoryEmbeddingStore<TextSegment> embeddingStore;
    private AllMiniLmL6V2EmbeddingModel embeddingModel;
    private ResearchRagProperties properties;
    private TrialCriteriaIngestionService ingestionService;
    private TrialCriteriaRetriever retriever;

    @BeforeEach
    void setUp() {
        trialVersionRepository = mock(TrialVersionRepository.class);
        ragIndexRepository = mock(EligibilityRagIndexRepository.class);
        embeddingStore = new InMemoryEmbeddingStore<>();
        embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        properties = new ResearchRagProperties();
        properties.setTopK(5);

        ingestionService = new TrialCriteriaIngestionService(
                trialVersionRepository,
                ragIndexRepository,
                embeddingStore,
                embeddingModel,
                new ObjectMapper()
        );

        retriever = new TrialCriteriaRetriever(
                trialVersionRepository,
                ingestionService,
                embeddingStore,
                embeddingModel,
                properties
        );
    }

    @Test
    void testRetrievalScopedToTrialVersionAndMetadataPreserved() {
        // Trial A: Diabetes Study
        UUID trialAId = UUID.randomUUID();
        UUID versionAId = UUID.randomUUID();
        TrialVersion versionA = new TrialVersion(trialAId, 1);
        versionA.setId(versionAId);
        versionA.setStatus(VersionStatus.approved);

        Criterion ca1 = new Criterion(versionAId, CriterionKind.INCLUSION, 1, "Diagnosis of Type 2 Diabetes Mellitus");
        ca1.setNormalizedRule("{\"fact\":\"condition.diabetes\"}");
        Criterion ca2 = new Criterion(versionAId, CriterionKind.INCLUSION, 2, "HbA1c between 7.0% and 10.5%");
        versionA.getCriteria().addAll(List.of(ca1, ca2));

        // Trial B: Oncology Study
        UUID trialBId = UUID.randomUUID();
        UUID versionBId = UUID.randomUUID();
        TrialVersion versionB = new TrialVersion(trialBId, 1);
        versionB.setId(versionBId);
        versionB.setStatus(VersionStatus.approved);

        Criterion cb1 = new Criterion(versionBId, CriterionKind.INCLUSION, 1, "Histologically confirmed non-small cell lung cancer");
        versionB.getCriteria().add(cb1);

        when(trialVersionRepository.findById(versionAId)).thenReturn(Optional.of(versionA));
        when(trialVersionRepository.findById(versionBId)).thenReturn(Optional.of(versionB));
        when(ragIndexRepository.findByTrialVersionId(versionAId)).thenReturn(Optional.empty());
        when(ragIndexRepository.findByTrialVersionId(versionBId)).thenReturn(Optional.empty());

        // Index both trials
        ingestionService.indexTrialVersion(versionAId);
        ingestionService.indexTrialVersion(versionBId);

        // Retrieve scoped to Trial A
        TrialCriteriaRetrieveResponse responseA = retriever.retrieve(versionAId, "diabetes HbA1c", 5);

        assertNotNull(responseA);
        assertEquals(versionAId, responseA.trialVersionId());
        assertFalse(responseA.criteria().isEmpty());

        // Verify all returned criteria belong strictly to Trial A
        for (RetrievedCriterionDto dto : responseA.criteria()) {
            assertTrue(dto.criterionId().equals(ca1.getId()) || dto.criterionId().equals(ca2.getId()),
                    "Criterion must belong to Trial A, but was: " + dto.criterionId());
            assertNotNull(dto.provenance());
            assertTrue(dto.provenance().contains(versionAId.toString()));
            assertNotNull(dto.sourceText());
        }

        // Retrieve scoped to Trial B for diabetes
        TrialCriteriaRetrieveResponse responseB = retriever.retrieve(versionBId, "diabetes", 5);
        assertEquals(versionBId, responseB.trialVersionId());
        // Trial B's results must NEVER contain Trial A's criterion IDs
        for (RetrievedCriterionDto dto : responseB.criteria()) {
            assertEquals(cb1.getId(), dto.criterionId());
        }
    }

    @Test
    void testRejectsUnapprovedTrialVersion() {
        UUID versionId = UUID.randomUUID();
        TrialVersion draftVersion = new TrialVersion(UUID.randomUUID(), 1);
        draftVersion.setId(versionId);
        draftVersion.setStatus(VersionStatus.draft);

        when(trialVersionRepository.findById(versionId)).thenReturn(Optional.of(draftVersion));

        ApplicationError error = assertThrows(ApplicationError.class,
                () -> retriever.retrieve(versionId, "diabetes", 5));

        assertEquals(422, error.getStatusCode());
        assertEquals("UNAPPROVED_TRIAL_VERSION", error.getCode());
    }

    @Test
    void testEmptyRetrievalOnEmptyCriteria() {
        UUID versionId = UUID.randomUUID();
        TrialVersion approvedEmpty = new TrialVersion(UUID.randomUUID(), 1);
        approvedEmpty.setId(versionId);
        approvedEmpty.setStatus(VersionStatus.approved);

        when(trialVersionRepository.findById(versionId)).thenReturn(Optional.of(approvedEmpty));

        TrialCriteriaRetrieveResponse response = retriever.retrieve(versionId, "anything", 5);

        assertNotNull(response);
        assertEquals(0, response.resultsCount());
        assertTrue(response.criteria().isEmpty());
    }
}
