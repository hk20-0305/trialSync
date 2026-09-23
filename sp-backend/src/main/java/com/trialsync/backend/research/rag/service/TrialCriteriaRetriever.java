package com.trialsync.backend.research.rag.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.entity.TrialVersion;
import com.trialsync.backend.entity.enums.VersionStatus;
import com.trialsync.backend.repository.TrialVersionRepository;
import com.trialsync.backend.research.rag.ResearchRagProperties;
import com.trialsync.backend.research.rag.dto.RetrievedCriterionDto;
import com.trialsync.backend.research.rag.dto.TrialCriteriaRetrieveResponse;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;

/**
 * Retrieves criteria from LangChain4j vector store, scoped strictly to the requested approved trial version.
 */
@Service
public class TrialCriteriaRetriever {

    private static final Logger log = LoggerFactory.getLogger(TrialCriteriaRetriever.class);

    private final TrialVersionRepository trialVersionRepository;
    private final TrialCriteriaIngestionService ingestionService;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final ResearchRagProperties properties;

    public TrialCriteriaRetriever(
            TrialVersionRepository trialVersionRepository,
            TrialCriteriaIngestionService ingestionService,
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            ResearchRagProperties properties) {
        this.trialVersionRepository = trialVersionRepository;
        this.ingestionService = ingestionService;
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    public TrialCriteriaRetrieveResponse retrieve(UUID versionId, String query, Integer userTopK) {
        TrialVersion trialVersion = trialVersionRepository.findById(versionId)
                .orElseThrow(() -> ApplicationError.notFound(
                        "TRIAL_VERSION_NOT_FOUND",
                        "Trial version " + versionId + " not found"));

        if (trialVersion.getStatus() != VersionStatus.approved) {
            throw ApplicationError.unprocessable(
                    "UNAPPROVED_TRIAL_VERSION",
                    "Retrieval is restricted to approved trial versions. Version "
                            + versionId + " has status: " + trialVersion.getStatus(),
                    "status");
        }

        int maxResults = (userTopK != null && userTopK > 0) ? userTopK : properties.getTopK();
        double minScore = properties.getMinScore();

        // 1. Scoped vector search filter: strictly metadata.trial_version_id == versionId
        Filter filter = MetadataFilterBuilder.metadataKey("trial_version_id").isEqualTo(versionId.toString());

        // 2. Embed user query
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .filter(filter)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

        // If no matches found initially, ensure trial version was indexed
        if (matches.isEmpty() && !trialVersion.getCriteria().isEmpty()) {
            log.info("No vectors found for trial version {}. Attempting auto-indexing.", versionId);
            ingestionService.indexTrialVersion(versionId);
            searchResult = embeddingStore.search(searchRequest);
            matches = searchResult.matches();
        }

        List<RetrievedCriterionDto> criteriaDtos = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : matches) {
            TextSegment segment = match.embedded();
            Metadata meta = segment.metadata();

            UUID criterionId = UUID.fromString(meta.getString("criterion_id"));
            String kind = meta.getString("kind");
            int order = meta.getInteger("order") != null ? meta.getInteger("order") : 0;
            String sourceText = meta.getString("source_text");
            String provenance = meta.getString("provenance");
            String ruleSummary = meta.getString("rule_summary");

            criteriaDtos.add(new RetrievedCriterionDto(
                    criterionId,
                    kind,
                    order,
                    sourceText,
                    provenance,
                    match.score(),
                    ruleSummary
            ));
        }

        return new TrialCriteriaRetrieveResponse(
                versionId,
                query,
                criteriaDtos.size(),
                criteriaDtos
        );
    }
}
