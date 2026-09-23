/**
 * Research-only Eligibility-Criteria RAG module.
 *
 * <p>This package will expose the LangChain + Gemini retrieval-augmented
 * generation (RAG) workflow. LangChain retrieves candidate trial versions from
 * TrialSync's approved criterion corpus; Gemini generates a schema-validated
 * structured eligibility summary grounded only in the retrieved criteria.
 * The existing Gemini implementation in {@code nlp/} is the provider base;
 * this module adds the retrieval orchestration layer.
 *
 * <p>The generated RAG summary is a presentation layer. The deterministic
 * screening engine in {@code domain/engine/} remains the final evaluator and
 * must never be replaced or bypassed by a RAG result.
 *
 * <p><strong>Status:</strong> Architecture scaffold — no implementation yet.
 * Implement only after R7 corpus and retrieval design are approved.
 *
 * <p>Planned responsibilities:
 * <ul>
 *   <li>RAG index build and corpus checksum storage (R7)</li>
 *   <li>LangChain retrieval orchestration (R7)</li>
 *   <li>Complete-criteria expansion per bounded candidate (R7)</li>
 *   <li>Gemini structured-summary request and validation (R7)</li>
 *   <li>Citation validation against stored criterion IDs (R7)</li>
 *   <li>Provider resilience: cooldown, cache, concurrency, retry (R7)</li>
 *   <li>RAG API routes (R7)</li>
 * </ul>
 */
@NonNullApi
package com.trialsync.backend.research.rag;

import org.springframework.lang.NonNullApi;
