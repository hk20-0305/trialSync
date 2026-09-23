/**
 * Research-only participant similarity module.
 *
 * <p>This package will serve FAISS nearest-neighbour query results built by
 * the {@code research-ml/cohort/similarity/faiss/} Python pipeline. Both the
 * patient-fact space and screening-profile space maintain separate, versioned
 * FAISS indexes. Similarity is not eligibility evidence and must never feed
 * into deterministic screening.
 *
 * <p><strong>Status:</strong> Architecture scaffold — no implementation yet.
 * Implement only after R6 FAISS indexes are built and approved.
 *
 * <p>Planned responsibilities:
 * <ul>
 *   <li>Similarity query request acceptance (R6)</li>
 *   <li>Index-version validation and mismatch rejection (R6)</li>
 *   <li>Neighbour result persistence and retrieval (R6)</li>
 *   <li>Feature-comparison delta serving (R6)</li>
 * </ul>
 */
@NonNullApi
package com.trialsync.backend.research.similarity;

import org.springframework.lang.NonNullApi;
