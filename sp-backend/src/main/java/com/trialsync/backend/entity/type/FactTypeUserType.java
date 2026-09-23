package com.trialsync.backend.entity.type;

import com.trialsync.backend.domain.model.FactType;

/** Maps {@link FactType} onto the PostgreSQL {@code fact_type} enum. */
public class FactTypeUserType extends PostgresEnumUserType<FactType> {

    public FactTypeUserType() {
        super(FactType.class, "fact_type", FactType::value, FactType::fromValue);
    }
}
