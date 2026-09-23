package com.trialsync.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.trialsync.backend.domain.model.Assertion;
import com.trialsync.backend.dto.patient.FactValue;

/**
 * Validates the {@code value} object on a fact request against the discriminated union that
 * describes it.
 *
 * <p>Python models this as {@code ConditionMedicationValue | PregnancyStatusValue |
 * NumericObservationValue} tagged on {@code input_kind}. The three shapes each declare
 * {@code extra="forbid"} and disagree on what is required - a status value may omit the effective
 * date, a pregnancy value must supply one, a numeric value must supply one and additionally ties
 * the presence of a reading to its assertion. Jackson's polymorphic deserialisation cannot express
 * per-shape unknown-key rejection while unknown properties stay tolerated globally, which they must
 * because Python leaves the patient profile models permissive. So the tree is walked here.
 *
 * <p>The discriminator has a default in every shape, but Pydantic still requires it to be present
 * to pick a member of a tagged union, so an absent {@code input_kind} is rejected rather than
 * guessed.
 */
@Component
public class FactValueParser {

    private static final Set<String> STATUS_FIELDS =
            Set.of("input_kind", "assertion", "effective_date");

    private static final Set<String> NUMERIC_FIELDS =
            Set.of("input_kind", "assertion", "value_numeric", "effective_date");

    /** {@code NumericObservationValue.assertion} is narrowed to these two. */
    private static final List<Assertion> NUMERIC_ASSERTIONS =
            List.of(Assertion.PRESENT, Assertion.UNKNOWN);

    /** Parses and validates one {@code value} object, or raises the fact-value error. */
    public FactValue parse(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw PatientValidationErrors.factValueInvalid(
                    "value",
                    "Input should be a valid dictionary or object to extract fields from",
                    "model_attributes_type");
        }
        JsonNode discriminator = value.get("input_kind");
        if (discriminator == null || !discriminator.isTextual()) {
            throw PatientValidationErrors.factValueInvalid(
                    "value.input_kind",
                    "Unable to extract tag using discriminator 'input_kind'",
                    "union_tag_not_found");
        }
        String inputKind = discriminator.asText();
        return switch (inputKind) {
            case FactValue.STATUS -> parseStatus(value);
            case FactValue.PREGNANCY_STATUS -> parsePregnancyStatus(value);
            case FactValue.NUMERIC -> parseNumeric(value);
            default ->
                    throw PatientValidationErrors.factValueInvalid(
                            "value.input_kind",
                            "Input tag '"
                                    + inputKind
                                    + "' found using 'input_kind' does not match any of the "
                                    + "expected tags: 'status', 'pregnancy_status', 'numeric'",
                            "union_tag_invalid");
        };
    }

    /** {@code ConditionMedicationValue}: an assertion, and optionally when it was assessed. */
    private FactValue parseStatus(JsonNode value) {
        rejectUnknownFields(value, STATUS_FIELDS);
        Assertion assertion = requiredAssertion(value, Assertion.values());
        LocalDate effectiveDate = optionalDate(value);
        return new FactValue(FactValue.STATUS, assertion, null, effectiveDate);
    }

    /** {@code PregnancyStatusValue}: the assessed date is mandatory, because the answer expires. */
    private FactValue parsePregnancyStatus(JsonNode value) {
        rejectUnknownFields(value, STATUS_FIELDS);
        Assertion assertion = requiredAssertion(value, Assertion.values());
        LocalDate effectiveDate = requiredDate(value);
        return new FactValue(FactValue.PREGNANCY_STATUS, assertion, null, effectiveDate);
    }

    /**
     * {@code NumericObservationValue}: a reading and the date it was taken.
     *
     * <p>The assertion narrows to present or unknown - a measurement cannot be "absent" - and the
     * reading must agree with it: present demands a value, unknown forbids one. Without that rule a
     * form could store an unexplained blank as though it were a measurement.
     */
    private FactValue parseNumeric(JsonNode value) {
        rejectUnknownFields(value, NUMERIC_FIELDS);
        Assertion assertion = Assertion.PRESENT;
        if (value.has("assertion")) {
            assertion = requiredAssertion(value, Assertion.PRESENT, Assertion.UNKNOWN);
        }
        BigDecimal valueNumeric = optionalDecimal(value);
        LocalDate effectiveDate = requiredDate(value);
        if (assertion == Assertion.PRESENT && valueNumeric == null) {
            throw PatientValidationErrors.factValueInvalid(
                    "value",
                    "Value error, A present numeric observation requires a value.",
                    "value_error");
        }
        if (assertion == Assertion.UNKNOWN && valueNumeric != null) {
            throw PatientValidationErrors.factValueInvalid(
                    "value",
                    "Value error, An unknown numeric observation cannot supply a value.",
                    "value_error");
        }
        return new FactValue(FactValue.NUMERIC, assertion, valueNumeric, effectiveDate);
    }

    /** Port of {@code extra="forbid"} on each union member. */
    private void rejectUnknownFields(JsonNode value, Set<String> allowed) {
        Iterator<String> names = value.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) {
                throw PatientValidationErrors.factValueInvalid(
                        "value." + name, "Extra inputs are not permitted", "extra_forbidden");
            }
        }
    }

    private Assertion requiredAssertion(JsonNode value, Assertion... permitted) {
        JsonNode node = value.get("assertion");
        if (node == null || node.isNull() || !node.isTextual()) {
            throw PatientValidationErrors.factValueInvalid(
                    "value.assertion",
                    node == null ? PatientValidationErrors.fieldRequired() : expectedAssertions(permitted),
                    node == null ? "missing" : "enum");
        }
        Assertion assertion = Assertion.fromValue(node.asText());
        if (assertion == null || !List.of(permitted).contains(assertion)) {
            throw PatientValidationErrors.factValueInvalid(
                    "value.assertion", expectedAssertions(permitted), "enum");
        }
        return assertion;
    }

    private static String expectedAssertions(Assertion... permitted) {
        StringBuilder message = new StringBuilder("Input should be ");
        for (int index = 0; index < permitted.length; index++) {
            if (index > 0) {
                message.append(index == permitted.length - 1 ? " or " : ", ");
            }
            message.append('\'').append(permitted[index].value()).append('\'');
        }
        return message.toString();
    }

    /** An absent or explicitly null {@code effective_date}, which the status shape allows. */
    private LocalDate optionalDate(JsonNode value) {
        JsonNode node = value.get("effective_date");
        if (node == null || node.isNull()) {
            return null;
        }
        return parseDate(node);
    }

    private LocalDate requiredDate(JsonNode value) {
        JsonNode node = value.get("effective_date");
        if (node == null || node.isNull()) {
            throw PatientValidationErrors.factValueInvalid(
                    "value.effective_date",
                    node == null
                            ? PatientValidationErrors.fieldRequired()
                            : "Input should be a valid date",
                    node == null ? "missing" : "date_type");
        }
        return parseDate(node);
    }

    private LocalDate parseDate(JsonNode node) {
        if (!node.isTextual()) {
            throw PatientValidationErrors.factValueInvalid(
                    "value.effective_date", "Input should be a valid date", "date_type");
        }
        try {
            return LocalDate.parse(node.asText().strip());
        } catch (DateTimeParseException exception) {
            throw PatientValidationErrors.factValueInvalid(
                    "value.effective_date",
                    "Input should be a valid date in the format YYYY-MM-DD",
                    "date_from_datetime_parsing");
        }
    }

    /**
     * The reading. Accepts a JSON number or a numeric string, matching Pydantic's {@code Decimal},
     * and keeps the caller's scale so an audit entry written before the row is re-read reports the
     * value exactly as it was submitted.
     */
    private BigDecimal optionalDecimal(JsonNode value) {
        JsonNode node = value.get("value_numeric");
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            try {
                return new BigDecimal(node.asText().strip());
            } catch (NumberFormatException exception) {
                throw decimalInvalid();
            }
        }
        throw decimalInvalid();
    }

    private static RuntimeException decimalInvalid() {
        return PatientValidationErrors.factValueInvalid(
                "value.value_numeric",
                "Input should be a valid decimal",
                "decimal_parsing");
    }
}
