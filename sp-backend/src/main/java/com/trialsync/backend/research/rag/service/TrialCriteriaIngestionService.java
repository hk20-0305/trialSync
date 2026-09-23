package com.trialsync.backend.research.rag.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.entity.Criterion;
import com.trialsync.backend.entity.EligibilityRagIndex;
import com.trialsync.backend.entity.TrialVersion;
import com.trialsync.backend.entity.enums.VersionStatus;
import com.trialsync.backend.repository.EligibilityRagIndexRepository;
import com.trialsync.backend.repository.TrialVersionRepository;
import com.trialsync.backend.research.rag.dto.TrialCriteriaIngestResponse;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;

/**
 * Service for indexing eligibility criteria of approved trial versions into the LangChain4j vector store.
 * Strictly enforces that only APPROVED trial versions may be indexed.
 */
@Service
public class TrialCriteriaIngestionService {

    private static final Logger log = LoggerFactory.getLogger(TrialCriteriaIngestionService.class);

    private final TrialVersionRepository trialVersionRepository;
    private final EligibilityRagIndexRepository ragIndexRepository;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;

    public TrialCriteriaIngestionService(
            TrialVersionRepository trialVersionRepository,
            EligibilityRagIndexRepository ragIndexRepository,
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            ObjectMapper objectMapper) {
        this.trialVersionRepository = trialVersionRepository;
        this.ragIndexRepository = ragIndexRepository;
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TrialCriteriaIngestResponse indexTrialVersion(UUID versionId) {
        TrialVersion trialVersion = trialVersionRepository.findById(versionId)
                .orElseThrow(() -> ApplicationError.notFound(
                        "TRIAL_VERSION_NOT_FOUND",
                        "Trial version " + versionId + " not found"));

        // 1. Guard: Only APPROVED trial versions may be indexed.
        if (trialVersion.getStatus() != VersionStatus.approved) {
            throw ApplicationError.unprocessable(
                    "UNAPPROVED_TRIAL_VERSION",
                    "Only approved trial versions can be indexed in research RAG. Version "
                            + versionId + " has status: " + trialVersion.getStatus(),
                    "status");
        }

        List<Criterion> criteria = trialVersion.getCriteria();

        // 2. Remove any existing vector chunks for this trial version to support clean re-indexing
        try {
            Filter filter = MetadataFilterBuilder.metadataKey("trial_version_id").isEqualTo(versionId.toString());
            embeddingStore.removeAll(filter);
        } catch (Exception e) {
            log.debug("Vector purge for trial_version_id {} completed or empty: {}", versionId, e.getMessage());
        }

        // 3. Compute deterministic corpus checksum
        String corpusChecksum = computeChecksum(criteria);

        // 4. Index each eligibility criterion
        int indexedCount = 0;
        for (Criterion criterion : criteria) {
            String ruleSummary = extractRuleSummary(criterion.getNormalizedRule());
            String textPayload = buildTextPayload(criterion, ruleSummary);

            Metadata metadata = new Metadata();
            metadata.put("trial_id", trialVersion.getTrialId().toString());
            metadata.put("trial_version_id", versionId.toString());
            metadata.put("criterion_id", criterion.getId().toString());
            metadata.put("kind", criterion.getKind().name());
            metadata.put("order", criterion.getOrder());
            metadata.put("source_text", criterion.getSourceText());
            metadata.put("provenance", "TrialSync:TrialVersion:" + versionId + ":Criterion:" + criterion.getId());
            if (ruleSummary != null) {
                metadata.put("rule_summary", ruleSummary);
            }

            TextSegment segment = TextSegment.from(textPayload, metadata);
            Embedding embedding = embeddingModel.embed(segment).content();
            embeddingStore.add(embedding, segment);
            indexedCount++;
        }

        // 5. Update or insert database index record
        EligibilityRagIndex ragIndex = ragIndexRepository.findByTrialVersionId(versionId)
                .orElseGet(() -> new EligibilityRagIndex(versionId, corpusChecksum, criteria.size(), "v1"));

        ragIndex.setCorpusChecksum(corpusChecksum);
        ragIndex.setChunkCount(indexedCount);
        ragIndex.setIndexedAt(OffsetDateTime.now());
        ragIndex.setStatus("INDEXED");
        ragIndexRepository.save(ragIndex);

        log.info("Successfully indexed {} criteria for approved trial version {}", indexedCount, versionId);

        return new TrialCriteriaIngestResponse(
                versionId,
                "INDEXED",
                indexedCount,
                corpusChecksum,
                ragIndex.getIndexedAt(),
                "Successfully indexed " + indexedCount + " eligibility criteria for approved trial version " + versionId
        );
    }

    private String buildTextPayload(Criterion criterion, String ruleSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("Criterion Type: ").append(criterion.getKind().name()).append("\n");
        sb.append("Requirement: ").append(criterion.isRequired() ? "REQUIRED" : "OPTIONAL").append("\n");
        sb.append("Clinical Criterion: ").append(criterion.getSourceText()).append("\n");
        if (ruleSummary != null && !ruleSummary.isBlank()) {
            sb.append("Structured Rule: ").append(ruleSummary);
        }
        return sb.toString();
    }

    private String extractRuleSummary(String normalizedRuleJson) {
        if (normalizedRuleJson == null || normalizedRuleJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(normalizedRuleJson);
            return node.toString();
        } catch (Exception e) {
            return normalizedRuleJson;
        }
    }

    private String computeChecksum(List<Criterion> criteria) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (Criterion c : criteria) {
                md.update(c.getId().toString().getBytes(StandardCharsets.UTF_8));
                md.update(c.getSourceText().getBytes(StandardCharsets.UTF_8));
                if (c.getNormalizedRule() != null) {
                    md.update(c.getNormalizedRule().getBytes(StandardCharsets.UTF_8));
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not found", e);
        }
    }
}
