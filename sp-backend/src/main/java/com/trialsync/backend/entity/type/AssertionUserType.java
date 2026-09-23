package com.trialsync.backend.entity.type;

import com.trialsync.backend.domain.model.Assertion;

/** Maps {@link Assertion} onto the PostgreSQL {@code fact_assertion} enum. */
public class AssertionUserType extends PostgresEnumUserType<Assertion> {

    public AssertionUserType() {
        super(Assertion.class, "fact_assertion", Assertion::value, Assertion::fromValue);
    }
}
