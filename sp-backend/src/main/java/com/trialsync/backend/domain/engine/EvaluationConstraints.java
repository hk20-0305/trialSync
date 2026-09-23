package com.trialsync.backend.domain.engine;

/**
 * Temporal narrowing inherited from enclosing {@code current} / {@code within_before} operators.
 *
 * <p>Port of the private {@code _Constraints} dataclass in {@code trialsync.domain.engine}. A null
 * {@code withinDays} means "no recency window".
 */
record EvaluationConstraints(boolean currentOnly, Integer withinDays) {

    static final EvaluationConstraints DEFAULT = new EvaluationConstraints(false, null);
}
