package com.trialsync.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trialsync.backend.domain.model.Assertion;
import com.trialsync.backend.domain.model.Fact;
import com.trialsync.backend.domain.model.FactType;
import com.trialsync.backend.domain.model.Temporality;
import com.trialsync.backend.entity.Patient;
import com.trialsync.backend.entity.PatientFact;
import com.trialsync.backend.entity.PatientSnapshot;
import com.trialsync.backend.repository.PatientSnapshotRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Freezes a patient into an immutable snapshot and turns a stored snapshot back into the domain
 * shape the engine consumes. Port of the snapshot half of {@code trialsync.screening.service}.
 *
 * <p>The content hash produced here is a compatibility surface, not an implementation detail: it is
 * the snapshot's identity ({@code snapshot_version}), the reuse key on
 * {@code (patient_id, content_hash)}, and a printed line on the audit report. See
 * {@link CanonicalJson} for why the bytes it hashes are assembled by hand.
 */
@Service
public class SnapshotService {

    /** Port of {@code ENGINE_VERSION}. */
    public static final String ENGINE_VERSION = "0.1.0";

    private static final TypeReference<List<Map<String, Object>>> FACT_LIST =
            new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> SOURCE_SUMMARY =
            new TypeReference<>() {};

    private final PatientSnapshotRepository patientSnapshots;
    private final ObjectMapper objectMapper;

    public SnapshotService(PatientSnapshotRepository patientSnapshots, ObjectMapper objectMapper) {
        this.patientSnapshots = patientSnapshots;
        this.objectMapper = objectMapper;
    }

    /** What {@code _snapshot_payload} returns: the hash, the fact payloads and the source summary. */
    public record SnapshotContent(
            String contentHash, List<Map<String, Object>> facts, Map<String, Object> source) {}

    /**
     * Port of {@code _fact_payload}.
     *
     * <p>Insertion order matches the Python dict so the stored {@code facts_json} - which the API
     * echoes back verbatim - presents its keys in the same order. The canonical hash does not depend
     * on this, because the encoder sorts keys, but the response body does.
     *
     * <p>{@code value_numeric} is rendered with {@link BigDecimal#toString()}, the exact counterpart
     * of Python's {@code str(Decimal)}: a value read from the {@code numeric(18,6)} column keeps its
     * scale, so {@code 55.5} is written as {@code "55.500000"} on both sides.
     */
    public Map<String, Object> factPayload(PatientFact fact) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", fact.getId().toString());
        payload.put("fact_type", fact.getFactType().value());
        payload.put("concept", fact.getConcept());
        payload.put(
                "value_numeric",
                fact.getValueNumeric() == null ? null : fact.getValueNumeric().toString());
        payload.put("value_text", fact.getValueText());
        payload.put("unit", fact.getUnit());
        payload.put("assertion", fact.getAssertion().value());
        payload.put(
                "effective_date",
                fact.getEffectiveDate() == null ? null : fact.getEffectiveDate().toString());
        payload.put("source_label", fact.getSourceLabel());
        return payload;
    }

    /**
     * Port of {@code _snapshot_payload}.
     *
     * <p>Facts are sorted by their identifier <em>as a string</em>, not as a UUID, because Python
     * sorts {@code str(value["id"])}: the two orders differ, and the hash depends on which one is
     * used. The canonical document has exactly three top-level members - {@code date_of_birth},
     * {@code facts} and {@code sex} - and the encoder emits them in that sorted order.
     */
    public SnapshotContent buildContent(Patient patient) {
        List<Map<String, Object>> facts = new ArrayList<>();
        for (PatientFact fact : patient.getFacts()) {
            facts.add(factPayload(fact));
        }
        facts.sort(
                (left, right) ->
                        CanonicalJson.compareByCodePoint(
                                String.valueOf(left.get("id")), String.valueOf(right.get("id"))));

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("patient_id", patient.getId().toString());
        source.put("external_id", patient.getExternalId());
        source.put("display_name", patient.getDisplayName());
        source.put("sex", patient.getSex());

        Map<String, Object> canonicalDocument = new LinkedHashMap<>();
        canonicalDocument.put(
                "date_of_birth",
                patient.getDateOfBirth() == null ? null : patient.getDateOfBirth().toString());
        canonicalDocument.put("sex", patient.getSex());
        canonicalDocument.put("facts", facts);

        String canonical = CanonicalJson.write(canonicalDocument);
        return new SnapshotContent(sha256Hex(canonical), facts, source);
    }

    /**
     * Port of {@code snapshot_for_patient}: reuse an identical snapshot, otherwise write a new one.
     *
     * <p>Re-screening an unchanged patient must not accumulate near-duplicate snapshots, so the
     * lookup is by {@code (patient_id, content_hash)} and the new row's {@code snapshot_version} is
     * the hash itself. The insert is flushed immediately because the screening row that follows
     * references it.
     */
    public PatientSnapshot snapshotForPatient(Patient patient) {
        SnapshotContent content = buildContent(patient);
        Optional<PatientSnapshot> existing =
                patientSnapshots.findByPatientIdAndContentHash(patient.getId(), content.contentHash());
        if (existing.isPresent()) {
            return existing.get();
        }
        PatientSnapshot snapshot =
                new PatientSnapshot(patient.getOwnerId(), patient.getId(), content.contentHash());
        snapshot.setDateOfBirth(patient.getDateOfBirth());
        snapshot.setFactsJson(writeJson(content.facts()));
        snapshot.setSourceSummary(writeJson(content.source()));
        return patientSnapshots.saveAndFlush(snapshot);
    }

    /**
     * Port of {@code _domain_snapshot}: rebuild the engine's view from the frozen row.
     *
     * <p>Nothing mutable is consulted. Numeric values are revived through {@code Decimal(str(...))},
     * which here is {@link BigDecimal#BigDecimal(String)} over the stored string, so the scale that
     * was hashed is the scale the engine compares with.
     *
     * <p>The synthetic {@code demographic.sex} fact is appended exactly as Python appends it - only
     * when the source summary holds a non-blank string - because trial rules address biological sex
     * through the fact path rather than through a snapshot column.
     */
    public com.trialsync.backend.domain.model.PatientSnapshot toDomain(PatientSnapshot snapshot) {
        List<Fact> facts = new ArrayList<>();
        for (Map<String, Object> raw : readFacts(snapshot)) {
            Object numeric = raw.get("value_numeric");
            BigDecimal numericValue = null;
            String textValue = null;
            if (numeric != null) {
                numericValue = new BigDecimal(String.valueOf(numeric));
            } else {
                Object text = raw.get("value_text");
                textValue = text == null ? null : String.valueOf(text);
            }
            Object effective = raw.get("effective_date");
            Object unit = raw.get("unit");
            facts.add(
                    Fact.builder(
                                    String.valueOf(raw.get("id")),
                                    factType(raw.get("fact_type")),
                                    String.valueOf(raw.get("concept")))
                            .numericValue(numericValue)
                            .textValue(textValue)
                            .unit(unit == null ? null : String.valueOf(unit))
                            .assertion(assertion(raw.get("assertion")))
                            .effectiveDate(
                                    isBlank(effective)
                                            ? null
                                            : LocalDate.parse(String.valueOf(effective)))
                            .sourceLabel(String.valueOf(raw.get("source_label")))
                            .temporality(Temporality.CURRENT)
                            .build());
        }

        Object sex = readSource(snapshot).get("sex");
        if (sex instanceof String text && !text.trim().isEmpty()) {
            String normalizedSex = text.trim().toLowerCase(java.util.Locale.ROOT);
            facts.add(
                    Fact.builder("demographic.sex", FactType.DEMOGRAPHIC, normalizedSex)
                            .textValue(normalizedSex)
                            .assertion(Assertion.PRESENT)
                            .sourceLabel("Patient profile")
                            .temporality(Temporality.CURRENT)
                            .build());
        }

        return new com.trialsync.backend.domain.model.PatientSnapshot(
                snapshot.getId().toString(),
                snapshot.getSnapshotVersion(),
                snapshot.getDateOfBirth(),
                facts);
    }

    /** The stored fact payloads, parsed. The list is what the API returns as {@code facts}. */
    public List<Map<String, Object>> readFacts(PatientSnapshot snapshot) {
        String json = snapshot.getFactsJson();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, FACT_LIST);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Stored snapshot facts could not be parsed", exception);
        }
    }

    /** The stored source summary, parsed: patient id, external id, display name and sex. */
    public Map<String, Object> readSource(PatientSnapshot snapshot) {
        String json = snapshot.getSourceSummary();
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, SOURCE_SUMMARY);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Stored snapshot source summary could not be parsed", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Snapshot payload could not be serialised", exception);
        }
    }

    /**
     * Python builds the enum with {@code FactType(str(...))}, which raises for an unrecognised value
     * rather than yielding a null the engine would then have to defend against. The same is done
     * here; the value can only be unrecognised if the stored JSON was written by something other
     * than {@link #factPayload}.
     */
    private static FactType factType(Object raw) {
        FactType resolved = FactType.fromValue(raw == null ? null : String.valueOf(raw));
        if (resolved == null) {
            throw new IllegalStateException("Unknown fact type in stored snapshot: " + raw);
        }
        return resolved;
    }

    private static Assertion assertion(Object raw) {
        Assertion resolved = Assertion.fromValue(raw == null ? null : String.valueOf(raw));
        if (resolved == null) {
            throw new IllegalStateException("Unknown assertion in stored snapshot: " + raw);
        }
        return resolved;
    }

    /** Python's {@code if effective:} - an absent, null or empty value all mean "no date". */
    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isEmpty();
    }

    private static String sha256Hex(String canonical) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the platform", exception);
        }
        byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            out.append(Character.forDigit((value >> 4) & 0xF, 16));
            out.append(Character.forDigit(value & 0xF, 16));
        }
        return out.toString();
    }
}
