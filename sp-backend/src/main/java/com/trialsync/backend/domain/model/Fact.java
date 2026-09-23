package com.trialsync.backend.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One clinical fact on a patient snapshot, as the engine sees it.
 *
 * <p>Port of {@code trialsync.domain.types.Fact}. Python types the value as {@code Decimal | str |
 * None}; here it is split into two nullable fields so that "is this numeric?" never depends on a
 * runtime cast. At most one of them is set, which mirrors the database, where a fact carries either
 * {@code value_numeric} or {@code value_text}.
 */
public record Fact(
        String id,
        FactType factType,
        String concept,
        BigDecimal numericValue,
        String textValue,
        String unit,
        Assertion assertion,
        Temporality temporality,
        LocalDate effectiveDate,
        String sourceLabel,
        String experiencer) {

    public static final String DEFAULT_SOURCE_LABEL = "Manual entry";
    public static final String PATIENT_EXPERIENCER = "patient";

    /** Applies the same defaults the Python dataclass declares. */
    public Fact {
        assertion = assertion == null ? Assertion.PRESENT : assertion;
        temporality = temporality == null ? Temporality.CURRENT : temporality;
        sourceLabel = sourceLabel == null ? DEFAULT_SOURCE_LABEL : sourceLabel;
        experiencer = experiencer == null ? PATIENT_EXPERIENCER : experiencer;
    }

    /** True when the fact carries a numeric value, i.e. Python's {@code isinstance(value, Decimal)}. */
    public boolean isNumeric() {
        return numericValue != null;
    }

    /**
     * Reproduces Python's {@code fact.assertion.value if fact.value is None else str(fact.value)}.
     *
     * <p>{@code BigDecimal.toString()} preserves scale exactly as {@code str(Decimal)} does, so a
     * value stored as {@code 7.20} renders as {@code "7.20"} on both sides.
     */
    public String evidenceValue() {
        if (numericValue != null) {
            return numericValue.toString();
        }
        if (textValue != null) {
            return textValue;
        }
        return assertion.value();
    }

    public static Builder builder(String id, FactType factType, String concept) {
        return new Builder(id, factType, concept);
    }

    /** Small builder so call sites are not a wall of positional nulls. */
    public static final class Builder {
        private final String id;
        private final FactType factType;
        private final String concept;
        private BigDecimal numericValue;
        private String textValue;
        private String unit;
        private Assertion assertion = Assertion.PRESENT;
        private Temporality temporality = Temporality.CURRENT;
        private LocalDate effectiveDate;
        private String sourceLabel = DEFAULT_SOURCE_LABEL;
        private String experiencer = PATIENT_EXPERIENCER;

        private Builder(String id, FactType factType, String concept) {
            this.id = id;
            this.factType = factType;
            this.concept = concept;
        }

        public Builder numericValue(BigDecimal value) {
            this.numericValue = value;
            return this;
        }

        public Builder textValue(String value) {
            this.textValue = value;
            return this;
        }

        public Builder unit(String value) {
            this.unit = value;
            return this;
        }

        public Builder assertion(Assertion value) {
            this.assertion = value;
            return this;
        }

        public Builder temporality(Temporality value) {
            this.temporality = value;
            return this;
        }

        public Builder effectiveDate(LocalDate value) {
            this.effectiveDate = value;
            return this;
        }

        public Builder sourceLabel(String value) {
            this.sourceLabel = value;
            return this;
        }

        public Builder experiencer(String value) {
            this.experiencer = value;
            return this;
        }

        public Fact build() {
            return new Fact(
                    id,
                    factType,
                    concept,
                    numericValue,
                    textValue,
                    unit,
                    assertion,
                    temporality,
                    effectiveDate,
                    sourceLabel,
                    experiencer);
        }
    }
}
