package com.trialsync.backend.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Tracks the indexing state, chunk count, and corpus checksum of an approved trial version
 * in the research RAG vector store.
 */
@Entity
@Table(name = "eligibility_rag_indexes")
public class EligibilityRagIndex extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "trial_version_id", nullable = false, columnDefinition = "uuid")
    private UUID trialVersionId;

    @Column(name = "corpus_checksum", nullable = false, length = 64)
    private String corpusChecksum;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount = 0;

    @Column(name = "index_version", nullable = false, length = 32)
    private String indexVersion = "v1";

    @Column(name = "embedding_model", nullable = false, length = 64)
    private String embeddingModel = "all-minilm-l6-v2";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "INDEXED";

    @Column(name = "indexed_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime indexedAt = OffsetDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trial_version_id", insertable = false, updatable = false)
    private TrialVersion trialVersion;

    protected EligibilityRagIndex() {
        // Required by JPA.
    }

    public EligibilityRagIndex(UUID trialVersionId, String corpusChecksum, int chunkCount, String indexVersion) {
        this.trialVersionId = trialVersionId;
        this.corpusChecksum = corpusChecksum;
        this.chunkCount = chunkCount;
        this.indexVersion = indexVersion;
        this.indexedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTrialVersionId() {
        return trialVersionId;
    }

    public void setTrialVersionId(UUID trialVersionId) {
        this.trialVersionId = trialVersionId;
    }

    public String getCorpusChecksum() {
        return corpusChecksum;
    }

    public void setCorpusChecksum(String corpusChecksum) {
        this.corpusChecksum = corpusChecksum;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getIndexVersion() {
        return indexVersion;
    }

    public void setIndexVersion(String indexVersion) {
        this.indexVersion = indexVersion;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getIndexedAt() {
        return indexedAt;
    }

    public void setIndexedAt(OffsetDateTime indexedAt) {
        this.indexedAt = indexedAt;
    }

    public TrialVersion getTrialVersion() {
        return trialVersion;
    }

    public void setTrialVersion(TrialVersion trialVersion) {
        this.trialVersion = trialVersion;
    }
}
