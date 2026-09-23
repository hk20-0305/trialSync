package com.trialsync.backend.dto.patient;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.trialsync.backend.domain.model.Assertion;

/**
 * The validated form of a clinical detail's {@code value} object.
 *
 * <p>Python models this as a union discriminated on {@code input_kind}
 * ({@code ConditionMedicationValue}, {@code PregnancyStatusValue}, {@code NumericObservationValue}),
 * each with {@code extra="forbid"} and its own required fields. The three shapes agree on the
 * fields that survive validation, so one flattened carrier holds the result and
 * {@code inputKind} keeps the discriminator the catalog check compares against.
 *
 * <p>{@code valueNumeric} is only ever populated for the {@code numeric} shape.
 */
public record FactValue(
        String inputKind, Assertion assertion, BigDecimal valueNumeric, LocalDate effectiveDate) {

    /** The {@code input_kind} discriminator values the contract accepts. */
    public static final String STATUS = "status";

    public static final String PREGNANCY_STATUS = "pregnancy_status";

    public static final String NUMERIC = "numeric";
}
