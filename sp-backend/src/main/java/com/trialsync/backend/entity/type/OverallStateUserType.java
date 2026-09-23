package com.trialsync.backend.entity.type;

import com.trialsync.backend.domain.model.OverallState;

/** Maps {@link OverallState} onto the PostgreSQL {@code overall_state} enum. */
public class OverallStateUserType extends PostgresEnumUserType<OverallState> {

    public OverallStateUserType() {
        super(OverallState.class, "overall_state", OverallState::value, OverallState::fromValue);
    }
}
