package com.trialsync.backend.domain.engine;

import com.trialsync.backend.domain.model.TruthValue;
import java.util.Collection;

/**
 * Kleene three-valued logic. Direct port of {@code trialsync.domain.logic}.
 *
 * <p>The subtle part is the empty case: an empty collection yields {@code UNKNOWN} for both AND and
 * OR, because Python guards the "all" branch with {@code if items and all(...)}. Java's
 * {@code allMatch} is vacuously true on an empty stream, so the emptiness check is explicit here.
 */
public final class ThreeValuedLogic {

    private ThreeValuedLogic() {}

    public static TruthValue not(TruthValue value) {
        if (value == TruthValue.TRUE) {
            return TruthValue.FALSE;
        }
        if (value == TruthValue.FALSE) {
            return TruthValue.TRUE;
        }
        return TruthValue.UNKNOWN;
    }

    public static TruthValue and(Collection<TruthValue> values) {
        if (values.contains(TruthValue.FALSE)) {
            return TruthValue.FALSE;
        }
        if (!values.isEmpty() && values.stream().allMatch(item -> item == TruthValue.TRUE)) {
            return TruthValue.TRUE;
        }
        return TruthValue.UNKNOWN;
    }

    public static TruthValue or(Collection<TruthValue> values) {
        if (values.contains(TruthValue.TRUE)) {
            return TruthValue.TRUE;
        }
        if (!values.isEmpty() && values.stream().allMatch(item -> item == TruthValue.FALSE)) {
            return TruthValue.FALSE;
        }
        return TruthValue.UNKNOWN;
    }
}
