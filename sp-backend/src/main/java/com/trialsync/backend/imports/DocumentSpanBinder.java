package com.trialsync.backend.imports;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.entity.Document;
import com.trialsync.backend.entity.DocumentSpan;
import com.trialsync.backend.entity.enums.DocumentKind;
import org.springframework.stereotype.Component;

/**
 * Binds candidates to persisted provenance: ports {@code _attach_spans} and
 * {@code _preserve_sources}.
 *
 * <p>Provenance is the reason this feature is safe to offer at all. Every candidate carries the page
 * and the exact characters it came from, and once a document is stored those live in
 * {@code document_spans} - a server-owned table the client never writes. From then on the server
 * rewrites each candidate's {@code source} block from the row rather than trusting what came back,
 * so a reviewer can change what a fact <em>says</em> but cannot change what the document said. A
 * candidate whose {@code span_id} does not resolve is rejected outright, not silently re-anchored.
 */
@Component
public class DocumentSpanBinder {

    /**
     * {@code _attach_spans}: creates one span per candidate and stamps its id back onto the
     * candidate's source block.
     *
     * <p>Runs while the document is still transient, so the spans reach the database with the
     * document in a single flush and the ids written into {@code candidates_json} are guaranteed to
     * exist.
     */
    public void attachSpans(Document document, ObjectNode candidates) {
        JsonNode items = candidates.get(itemsKey(document.getKind()));
        if (items == null || !items.isArray()) {
            return;
        }
        for (JsonNode item : items) {
            if (!item.isObject() || !item.path("source").isObject()) {
                continue;
            }
            ObjectNode source = (ObjectNode) item.get("source");
            DocumentSpan span =
                    new DocumentSpan(
                            source.path("page").asInt(),
                            source.path("start").asInt(),
                            source.path("end").asInt(),
                            source.path("text").asText());
            document.addSpan(span);
            source.put("span_id", span.getId().toString());
        }
    }

    /**
     * {@code _preserve_sources}: replaces every candidate's source block with the stored span.
     *
     * @throws ApplicationError 422 {@code IMPORT_PROVENANCE_INVALID} when a candidate names a span
     *     that does not belong to this document, which covers an absent {@code span_id}, a null one,
     *     one invented by the client and one copied from somebody else's import
     */
    public void preserveSources(Document document, ObjectNode candidates) {
        Map<String, DocumentSpan> spans = new HashMap<>();
        for (DocumentSpan span : document.getSpans()) {
            spans.put(span.getId().toString(), span);
        }
        JsonNode items = candidates.get(itemsKey(document.getKind()));
        if (items == null || !items.isArray()) {
            return;
        }
        for (JsonNode item : items) {
            if (!item.isObject() || !item.path("source").isObject()) {
                continue;
            }
            ObjectNode candidate = (ObjectNode) item;
            JsonNode spanId = candidate.get("source").get("span_id");
            DocumentSpan span =
                    spanId == null || !spanId.isTextual() ? null : spans.get(spanId.asText());
            if (span == null) {
                throw new ApplicationError(
                        "IMPORT_PROVENANCE_INVALID",
                        "Every extracted candidate must retain its original source span.",
                        422);
            }
            ObjectNode source = candidate.putObject("source");
            source.put("span_id", span.getId().toString());
            source.put("page", span.getPage());
            source.put("start", span.getStartOffset());
            source.put("end", span.getEndOffset());
            source.put("text", span.getExactText());
        }
    }

    /** Patient documents carry facts, trial documents carry criteria. */
    private static String itemsKey(DocumentKind kind) {
        return kind == DocumentKind.patient ? "facts" : "criteria";
    }
}
