package com.trialsync.backend.research.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import com.trialsync.backend.entity.EligibilityRagIndex;
import com.trialsync.backend.entity.TrialVersion;
import com.trialsync.backend.entity.enums.VersionStatus;
import com.trialsync.backend.repository.EligibilityRagIndexRepository;
import com.trialsync.backend.repository.TrialVersionRepository;
import com.trialsync.backend.research.rag.dto.TrialCriteriaIngestResponse;
import com.trialsync.backend.research.rag.service.TrialCriteriaIngestionService;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

class TrialCriteriaIngestionServiceTest {

    private TrialVersionRepository trialVersionRepository;
    private EligibilityRagIndexRepository ragIndexRepository;
    private InMemoryEmbeddingStore<TextSegment> embeddingStore;
    private AllMiniLmL6V2EmbeddingModel embeddingModel;
    private ObjectMapper objectMapper;
    private TrialCriteriaIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        trialVersionRepository = mock(TrialVersionRepository.class);
        ragIndexRepository = mock(EligibilityRagIndexRepository.class);
        embeddingStore = new InMemoryEmbeddingStore<>();
        embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        objectMapper = new ObjectMapper();

        ingestionService = new TrialCriteriaIngestionService(
                trialVersionRepository,
                ragIndexRepository,
                embeddingStore,
                embeddingModel,
                objectMapper
        );
    }

    @Test
    void testApprovedVersionIndexing() {
        UUID trialId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        TrialVersion version = new TrialVersion(trialId, 1);
        version.setId(versionId);
        version.setStatus(VersionStatus.approved);

        Criterion c1 = new Criterion(versionId, CriterionKind.INCLUSION, 1, "Patient must be >= 18 years old");
        c1.setNormalizedRule("{\"op\":\"gte\",\"fact\":\"demographic.age\",\"value\":18}");
        Criterion c2 = new Criterion(versionId, CriterionKind.EXCLUSION, 2, "History of severe heart failure");

        version.getCriteria().addAll(List.of(c1, c2));

        when(trialVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(ragIndexRepository.findByTrialVersionId(versionId)).thenReturn(Optional.empty());

        TrialCriteriaIngestResponse response = ingestionService.indexTrialVersion(versionId);

        assertNotNull(response);
        assertEquals(versionId, response.trialVersionId());
        assertEquals("INDEXED", response.status());
        assertEquals(2, response.chunkCount());
        assertNotNull(response.corpusChecksum());

        verify(ragIndexRepository).save(any(EligibilityRagIndex.class));
    }

    @Test
    void testRejectionOfUnapprovedVersions() {
        UUID trialId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        TrialVersion draftVersion = new TrialVersion(trialId, 1);
        draftVersion.setId(versionId);
        draftVersion.setStatus(VersionStatus.draft);

        when(trialVersionRepository.findById(versionId)).thenReturn(Optional.of(draftVersion));

        ApplicationError error = assertThrows(ApplicationError.class,
                () -> ingestionService.indexTrialVersion(versionId));

        assertEquals(422, error.getStatusCode());
        assertEquals("UNAPPROVED_TRIAL_VERSION", error.getCode());
        assertTrue(error.getMessage().contains("Only approved trial versions"));
    }

    @Test
    void testTrialVersionNotFound() {
        UUID nonExistentId = UUID.randomUUID();
        when(trialVersionRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        ApplicationError error = assertThrows(ApplicationError.class,
                () -> ingestionService.indexTrialVersion(nonExistentId));

        assertEquals(404, error.getStatusCode());
        assertEquals("TRIAL_VERSION_NOT_FOUND", error.getCode());
    }
}
