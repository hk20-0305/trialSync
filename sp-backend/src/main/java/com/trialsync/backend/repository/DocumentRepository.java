package com.trialsync.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.entity.Document;

/**
 * Uploaded or pasted source documents awaiting review.
 *
 * <p>There is no list endpoint - a document is only ever fetched by id, and always with the owner in
 * the predicate. Extracted spans are mapped as a cascading child collection, so loading the document
 * and touching {@code getSpans()} reproduces {@code selectinload(Document.spans)}.
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /** The only read path: {@code where(Document.id == import_id, Document.owner_id == user.id)}. */
    Optional<Document> findByIdAndOwnerId(UUID id, UUID ownerId);
}
