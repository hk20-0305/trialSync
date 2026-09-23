package com.trialsync.backend.domain.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.trialsync.backend.domain.model.TruthValue;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ThreeValuedLogicTest {

    @ParameterizedTest
    @CsvSource({
        "TRUE, TRUE, TRUE, TRUE",
        "TRUE, FALSE, FALSE, TRUE",
        "TRUE, UNKNOWN, UNKNOWN, TRUE",
        "FALSE, TRUE, FALSE, TRUE",
        "FALSE, FALSE, FALSE, FALSE",
        "FALSE, UNKNOWN, FALSE, UNKNOWN",
        "UNKNOWN, TRUE, UNKNOWN, TRUE",
        "UNKNOWN, FALSE, FALSE, UNKNOWN",
        "UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN"
    })
    void testAndOrTruthTables(
            TruthValue left, TruthValue right, TruthValue expectedAnd, TruthValue expectedOr) {
        assertEquals(expectedAnd, ThreeValuedLogic.and(List.of(left, right)));
        assertEquals(expectedOr, ThreeValuedLogic.or(List.of(left, right)));
    }

    @ParameterizedTest
    @CsvSource({
        "TRUE, FALSE",
        "FALSE, TRUE",
        "UNKNOWN, UNKNOWN"
    })
    void testNotTruthTable(TruthValue value, TruthValue expected) {
        assertEquals(expected, ThreeValuedLogic.not(value));
    }

    @Test
    void testEmptyCollectionsYieldUnknown() {
        assertEquals(TruthValue.UNKNOWN, ThreeValuedLogic.and(List.of()));
        assertEquals(TruthValue.UNKNOWN, ThreeValuedLogic.or(List.of()));
    }
}
