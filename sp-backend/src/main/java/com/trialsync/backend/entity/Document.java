package com.trialsync.backend.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import com.trialsync.backend.entity.enums.DocumentKind;
import com.trialsync.backend.entity.enums.DocumentSourceType;
import com.trialsync.backend.entity.enums.DocumentStatus;
import com.trialsync.backend.entity.type.JsonStringUserType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * An uploaded or pasted document held for human review before it becomes a patient or trial.
 *
 * <p>The four JSON columns are round-tripped to the review UI unchanged, so they are stored as raw
 * text. {@code approved_resource_id} points at the patient or trial that approval produced; it is
 * deliberately not a foreign key in the Python schema and is not one here either.
 */
@Entity
@Table(name = "documents")
public class Document extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "owner_id", nullable = false, columnDefinition = "uuid")
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "kind", nullable = false, columnDefinition = "document_kind")
    private DocumentKind kind;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "source_type", nullable = false, columnDefinition = "document_source_type")
    private DocumentSourceType sourceType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "document_status")
    private DocumentStatus status = DocumentStatus.needs_review;

    @Column(name = "filename", length = 255)
    private String filename;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;

    /** The original PDF bytes, retained so a reviewer can re-open exactly what was uploaded. */
    @Column(name = "original_content", columnDefinition = "bytea")
    private byte[] originalContent;

    @Column(name = "source_text", nullable = false, columnDefinition = "text")
    private String sourceText;

    @Type(JsonStringUserType.class)
    @Column(name = "pages_json", nullable = false, columnDefinition = "json")
    private String pagesJson;

    @Type(JsonStringUserType.class)
    @Column(name = "candidates_json", nullable = false, columnDefinition = "json")
    private String candidatesJson;

    @Type(JsonStringUserType.class)
    @Column(name = "warnings_json", nullable = false, columnDefinition = "json")
    private String warningsJson;

    @Type(JsonStringUserType.class)
    @Column(name = "quality_json", nullable = false, columnDefinition = "json")
    private String qualityJson;

    @Column(name = "approved_resource_id", columnDefinition = "uuid")
    private UUID approvedResourceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", insertable = false, updatable = false)
    private User owner;

    /**
     * Spans are created while the document is still transient and their ids are written into
     * {@code candidates_json}, so this is the one association in the schema that is managed from the
     * parent instead of by assigning a foreign key.
     */
    @OneToMany(
            mappedBy = "document",
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    @OrderBy("page")
    @BatchSize(size = 100)
    private List<DocumentSpan> spans = new ArrayList<>();

    protected Document() {
        // Required by JPA.
    }

    public Document(UUID ownerId, DocumentKind kind, DocumentSourceType sourceType) {
        this.ownerId = ownerId;
        this.kind = kind;
        this.sourceType = sourceType;
    }

    /** Links a span to this document from both sides so the cascade sees it. */
    public void addSpan(DocumentSpan span) {
        spans.add(span);
        span.setDocument(this);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public DocumentKind getKind() {
        return kind;
    }

    public void setKind(DocumentKind kind) {
        this.kind = kind;
    }

    public DocumentSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(DocumentSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(int sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public byte[] getOriginalContent() {
        return originalContent;
    }

    public void setOriginalContent(byte[] originalContent) {
        this.originalContent = originalContent;
    }

    public String getSourceText() {
        return sourceText;
    }

    public void setSourceText(String sourceText) {
        this.sourceText = sourceText;
    }

    public String getPagesJson() {
        return pagesJson;
    }

    public void setPagesJson(String pagesJson) {
        this.pagesJson = pagesJson;
    }

    public String getCandidatesJson() {
        return candidatesJson;
    }

    public void setCandidatesJson(String candidatesJson) {
        this.candidatesJson = candidatesJson;
    }

    public String getWarningsJson() {
        return warningsJson;
    }

    public void setWarningsJson(String warningsJson) {
        this.warningsJson = warningsJson;
    }

    public String getQualityJson() {
        return qualityJson;
    }

    public void setQualityJson(String qualityJson) {
        this.qualityJson = qualityJson;
    }

    public UUID getApprovedResourceId() {
        return approvedResourceId;
    }

    public void setApprovedResourceId(UUID approvedResourceId) {
        this.approvedResourceId = approvedResourceId;
    }

    public User getOwner() {
        return owner;
    }

    public List<DocumentSpan> getSpans() {
        return spans;
    }
}
