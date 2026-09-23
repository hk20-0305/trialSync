/**
 * Research-only dropout-risk module.
 *
 * <p>This package will expose Java service interfaces and API controllers that
 * delegate ML inference to the {@code research-ml/dropout/} Python pipeline.
 * The dropout model produces a synthetic research probability that must never
 * alter deterministic screening results.
 *
 * <p><strong>Status:</strong> Architecture scaffold — no implementation yet.
 * Implement only after R3 dataset and R4 model experiments are approved.
 *
 * <p>Planned responsibilities:
 * <ul>
 *   <li>Research enrollment link persistence (R5)</li>
 *   <li>Model-version registry queries (R5)</li>
 *   <li>Research prediction storage and retrieval (R5)</li>
 *   <li>Trial recruitment overview aggregation (R5)</li>
 *   <li>Missed-dose Scenario Lab feature recomputation (R5)</li>
 * </ul>
 */
@NonNullApi
package com.trialsync.backend.research.dropout;

import org.springframework.lang.NonNullApi;
