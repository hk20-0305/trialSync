package com.trialsync.backend.entity.type;

import com.trialsync.backend.domain.model.CriterionResult;

/**
 * Maps {@link CriterionResult} onto the PostgreSQL {@code evaluation_result} enum.
 *
 * <p>The Python enum member is {@code pass_} because {@code pass} is a reserved word, and the
 * revision declares {@code values_callable} so the stored label is {@code pass}. This mapping binds
 * the label, so the {@code PASS} constant is stored as {@code pass}.
 */
public class CriterionResultUserType extends PostgresEnumUserType<CriterionResult> {

    public CriterionResultUserType() {
        super(
                CriterionResult.class,
                "evaluation_result",
                CriterionResult::value,
                CriterionResult::fromValue);
    }
}
