/**
 * Research-only cohort discovery module.
 *
 * <p>This package will expose Java service interfaces and API controllers for
 * the screening-derived Cohort Atlas. DBSCAN clustering and FAISS similarity
 * are computed by the {@code research-ml/cohort/} Python pipeline; this
 * package serves the stored results through the REST API.
 *
 * <p><strong>Status:</strong> Phase R9 active implementation.
 * Exposes Spring WebClient proxying to the Python {@code research-ml} Cohort Atlas service.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Cohort health and readiness retrieval</li>
 *   <li>Cohort summary and metadata retrieval</li>
 *   <li>Paginated 2D PCA projection coordinates and cluster filtering</li>
 *   <li>DBSCAN cluster summary profiles</li>
 *   <li>FAISS exact CPU nearest-neighbor peer similarity queries</li>
 * </ul>
 */
@NonNullApi
package com.trialsync.backend.research.cohort;

import org.springframework.lang.NonNullApi;
