package com.trialsync.backend.entity.type;

import com.trialsync.backend.domain.model.CriterionKind;

/** Maps {@link CriterionKind} onto the PostgreSQL {@code criterion_kind} enum. */
public class CriterionKindUserType extends PostgresEnumUserType<CriterionKind> {

    public CriterionKindUserType() {
        super(
                CriterionKind.class,
                "criterion_kind",
                CriterionKind::value,
                CriterionKind::fromValue);
    }
}
