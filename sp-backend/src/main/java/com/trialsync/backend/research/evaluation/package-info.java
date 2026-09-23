/**
 * Research evaluation harness module.
 *
 * <p>This package will provide Java-side utilities for loading frozen fixture
 * sets, computing retrieval metrics (Recall@k, MRR), and verifying that
 * research API responses comply with declared schemas and disclaimer
 * requirements. It mirrors the Python {@code evaluation.py} evaluation harness
 * for the research extension.
 *
 * <p><strong>Status:</strong> Architecture scaffold — no implementation yet.
 * Implement only during R8 integrated evaluation.
 *
 * <p>Planned responsibilities:
 * <ul>
 *   <li>Frozen RAG query fixture loading and Recall@k evaluation (R8)</li>
 *   <li>Criterion-citation precision checks (R8)</li>
 *   <li>Research API contract verification (R8)</li>
 *   <li>Integrated evaluation report generation (R8)</li>
 * </ul>
 */
@NonNullApi
package com.trialsync.backend.research.evaluation;

import org.springframework.lang.NonNullApi;
