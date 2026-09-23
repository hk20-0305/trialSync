package com.trialsync.backend.nlp;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Elapsed-time helpers that reproduce {@code round(..., 2)} on a millisecond figure.
 *
 * <p>Latency lands in stored extraction metadata and in the chat metrics log, so it has to round
 * the way CPython rounds: half-to-even on the exact binary value of the double, not on its shortest
 * printed form. {@link BigDecimal#BigDecimal(double)} takes the exact value, which is what makes the
 * two agree.
 *
 * <p>{@code System.nanoTime()} is the counterpart of {@code time.perf_counter()}: monotonic, and
 * unaffected by clock adjustments.
 */
public final class Latency {

    private Latency() {}

    /** {@code time.perf_counter()}. */
    public static long start() {
        return System.nanoTime();
    }

    /** {@code round((time.perf_counter() - started) * 1_000, 2)}. */
    public static double millisSince(long startNanos) {
        return round2((System.nanoTime() - startNanos) / 1_000_000.0d);
    }

    /** {@code round(value, 2)} with CPython's half-to-even behaviour. */
    public static double round2(double value) {
        if (!Double.isFinite(value)) {
            return value;
        }
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_EVEN).doubleValue();
    }
}
