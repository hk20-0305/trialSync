package com.trialsync.backend.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * The exact stretch of source text an extracted candidate came from.
 *
 * <p>The table has no timestamps. Unlike every other child in the schema, the document association
 * is the writable owning side: spans are built while the document is still transient and their ids
 * are embedded in the document's {@code candidates_json}.
 */
@Entity
@Table(name = "document_spans")
public class DocumentSpan {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "page", nullable = false)
    private int page;

    @Column(name = "start_offset", nullable = false)
    private int startOffset;

    @Column(name = "end_offset", nullable = false)
    private int endOffset;

    @Column(name = "exact_text", nullable = false, columnDefinition = "text")
    private String exactText;

    protected DocumentSpan() {
        // Required by JPA.
    }

    public DocumentSpan(int page, int startOffset, int endOffset, String exactText) {
        this.page = page;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.exactText = exactText;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public void setStartOffset(int startOffset) {
        this.startOffset = startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    public void setEndOffset(int endOffset) {
        this.endOffset = endOffset;
    }

    public String getExactText() {
        return exactText;
    }

    public void setExactText(String exactText) {
        this.exactText = exactText;
    }
}
