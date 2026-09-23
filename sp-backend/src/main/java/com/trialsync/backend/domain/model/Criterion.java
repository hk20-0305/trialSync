package com.trialsync.backend.domain.model;

import java.util.Map;

/**
 * A single eligibility criterion together with its normalized rule expression.
 *
 * <p>Port of {@code trialsync.domain.types.Criterion}. Python freezes the expression behind a
 * {@code MappingProxyType}; {@link Map#copyOf} gives the same guarantee here. The copy is shallow,
 * matching Python, because nested rule maps are only ever read.
 */
public record Criterion(
        String id,
        CriterionKind kind,
        int order,
        String sourceText,
        Map<String, Object> expression,
        boolean required) {

    public Criterion {
        expression = expression == null ? Map.of() : Map.copyOf(expression);
    }

    public Criterion(String id, CriterionKind kind, int order, String sourceText, Map<String, Object> expression) {
        this(id, kind, order, sourceText, expression, true);
    }
}
