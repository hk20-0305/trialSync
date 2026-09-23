package com.trialsync.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.config.TrialSyncProperties;
import com.trialsync.backend.dto.imports.ImportAnalyzeRequest;
import com.trialsync.backend.dto.imports.ImportRead;
import com.trialsync.backend.dto.imports.ImportUpdateRequest;
import com.trialsync.backend.dto.imports.PatientImportCandidates;
import com.trialsync.backend.dto.imports.TrialImportCandidates;
import com.trialsync.backend.entity.Document;
import com.trialsync.backend.entity.DocumentSpan;
import com.trialsync.backend.entity.enums.DocumentKind;
import com.trialsync.backend.entity.enums.DocumentSourceType;
import com.trialsync.backend.entity.enums.DocumentStatus;
import com.trialsync.backend.imports.CandidateValidator;
import com.trialsync.backend.imports.DocumentSpanBinder;
import com.trialsync.backend.imports.DocumentTextExtractor;
import com.trialsync.backend.imports.ExtractedInput;
import com.trialsync.backend.imports.ImportCandidateValidationException;
import com.trialsync.backend.imports.ImportParseException;
import com.trialsync.backend.imports.PatientCandidateAnnotator;
import com.trialsync.backend.imports.PythonText;
import com.trialsync.backend.nlp.CandidateParser;
import com.trialsync.backend.nlp.ExtractionRun;
import com.trialsync.backend.nlp.ExtractorFactory;
import com.trialsync.backend.nlp.GroqExtractor;
import com.trialsync.backend.nlp.ProviderCallException;
import com.trialsync.backend.nlp.StructuredExtractor;
import com.trialsync.backend.repository.DocumentRepository;
import com.trialsync.backend.security.SecurityContext;

/**
 * Port of the analyse, read, review and reject routes of {@code trialsync.api.imports}.
 *
 * <p>An import is a proposal, never a record. Analysis stores extracted candidates and the exact
 * stretches of source text they came from; nothing reaches {@code patients} or {@code trials} until
 * {@link ImportApprovalService} runs, which is what keeps an extractor - deterministic or
 * language-model - out of the clinical data path.
 *
 * <p>Ownership is enforced by the lookup rather than by a separate check, as everywhere else in this
 * service layer: a document belonging to another account is reported as missing, so import ids
 * cannot be probed across tenants.
 *
 * <p>The four JSON columns are stored as text and parsed on the way out, because the review UI is
 * built on their exact key order. Every helper that hands a candidate document on to the next stage
 * therefore works on a mutable {@link ObjectNode} rather than re-marshalling through a map.
 */
@Service
public class ImportService {

    /** The document-level prefix {@code _annotate_patient_candidates} writes. */
    private static final String CATALOG_REVIEW_PREFIX = "Catalog review:";

    private final DocumentRepository documents;
    private final DocumentTextExtractor textExtractor;
    private final DocumentSpanBinder spanBinder;
    private final PatientCandidateAnnotator annotator;
    private final CandidateValidator validator;
    private final TrialSyncProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * {@code app.state.extractor}: the configured provider, resolved once at startup rather than per
     * request, so a deployment reads its credentials and provider mode exactly once.
     */
    private final StructuredExtractor extractor;

    /**
     * The deterministic extractor the analyse route re-runs when the configured provider fails.
     * Python constructs a fresh {@code RuleBasedExtractor()} at that point; it holds no state, so one
     * instance behaves identically.
     */
    private final StructuredExtractor fallbackExtractor;

    public ImportService(
            DocumentRepository documents,
            DocumentTextExtractor textExtractor,
            DocumentSpanBinder spanBinder,
            PatientCandidateAnnotator annotator,
            CandidateValidator validator,
            CandidateParser candidateParser,
            ExtractorFactory extractorFactory,
            TrialSyncProperties properties,
            ObjectMapper objectMapper) {
        this.documents = documents;
        this.textExtractor = textExtractor;
        this.spanBinder = spanBinder;
        this.annotator = annotator;
        this.validator = validator;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.extractor = extractorFactory.build(candidateParser);
        this.fallbackExtractor = extractorFactory.ruleBased(candidateParser);
    }

    // ---------------------------------------------------------------- lookups

    /**
     * Port of {@code owned_import()}.
     *
     * <p>The span collection is touched deliberately, reproducing
     * {@code selectinload(Document.spans)}: the review and approval paths both need it, and loading
     * it here keeps the association resolved inside the caller's transaction.
     */
    public Document ownedImport(UUID importId) {
        UUID ownerId = SecurityContext.require().getId();
        Document document =
                documents.findByIdAndOwnerId(importId, ownerId)
                        .orElseThrow(
                                () ->
                                        ApplicationError.notFound(
                                                "IMPORT_NOT_FOUND", "Import was not found."));
        for (DocumentSpan span : document.getSpans()) {
            span.getId();
        }
        return document;
    }

    /** Port of {@code import_read()}. */
    public ImportRead importRead(Document document) {
        return new ImportRead(
                document.getId(),
                document.getKind(),
                document.getSourceType(),
                document.getStatus().value(),
                document.getFilename(),
                document.getMimeType(),
                document.getSizeBytes(),
                document.getChecksum(),
                document.getSourceText(),
                readJson(document.getPagesJson()),
                readJson(document.getCandidatesJson()),
                readWarnings(document.getWarningsJson()),
                readJson(document.getQualityJson()),
                document.getApprovedResourceId(),
                document.getCreatedAt());
    }

    // -------------------------------------------------------------- endpoints

    /**
     * Port of {@code analyze_import()}.
     *
     * <p>Extraction failures are the interesting part. A parse failure - an encrypted PDF, a scan OCR
     * could not read, a payload over the size limit - is a 422 carrying the parser's own code, so the
     * reviewer is told what to do about it. A <em>provider</em> failure is not an error at all: the
     * deterministic parser is re-run, its candidates are returned, and the reason is recorded as a
     * visible warning plus {@code provider_error} in the stored metadata. An unavailable external
     * model must never be able to block an import.
     */
    @Transactional
    public ImportRead analyze(ImportAnalyzeRequest request) {
        ImportAnalyzeRequest payload = request.validated();
        UUID ownerId = SecurityContext.require().getId();

        byte[] original = null;
        ExtractedInput extracted;
        String mimeType;
        int sizeBytes;
        byte[] checksumContent;
        try {
            if (payload.sourceType() == DocumentSourceType.pdf) {
                original = decodePdf(payload);
                extracted = textExtractor.extractPdfInput(original);
                mimeType = "application/pdf";
                sizeBytes = original.length;
                checksumContent = original;
            } else {
                extracted =
                        textExtractor.extractTextInput(
                                payload.text() == null ? "" : payload.text());
                mimeType = "text/plain";
                checksumContent = extracted.text().getBytes(StandardCharsets.UTF_8);
                sizeBytes = checksumContent.length;
            }
        } catch (ImportParseException exception) {
            throw new ApplicationError(exception.getCode(), exception.getMessage(), 422);
        }

        ExtractionRun extraction = extract(payload.kind(), extracted);
        ObjectNode candidates = extraction.candidates();
        List<String> warnings = new ArrayList<>(extraction.warnings());
        if (payload.kind() == DocumentKind.patient) {
            PatientCandidateAnnotator.Annotated annotated = annotator.annotate(candidates);
            candidates = annotated.json();
            warnings.addAll(annotated.warnings());
        }

        Map<String, Object> quality = new LinkedHashMap<>(extracted.quality());
        quality.put("nlp", extraction.metadata());

        Document document = new Document(ownerId, payload.kind(), payload.sourceType());
        document.setStatus(DocumentStatus.needs_review);
        document.setFilename(payload.filename());
        document.setMimeType(mimeType);
        document.setSizeBytes(sizeBytes);
        document.setChecksum(sha256Hex(checksumContent));
        document.setOriginalContent(original);
        document.setSourceText(extracted.text());
        document.setPagesJson(writeJson(objectMapper.valueToTree(extracted.pages())));
        document.setWarningsJson(writeWarnings(warnings));
        document.setQualityJson(writeJson(objectMapper.valueToTree(quality)));

        // The spans have to exist before `candidates_json` is serialised: `attachSpans` stamps each
        // new span's id onto the candidate it came from, and those ids are what every later round
        // trip resolves provenance through.
        spanBinder.attachSpans(document, candidates);
        document.setCandidatesJson(writeJson(candidates));

        Document saved = documents.saveAndFlush(document);
        return importRead(ownedImport(saved.getId()));
    }

    /** Port of {@code get_import()}. */
    @Transactional(readOnly = true)
    public ImportRead get(UUID importId) {
        return importRead(ownedImport(importId));
    }

    /**
     * Port of {@code update_import()}: the reviewer's edit.
     *
     * <p>The submitted candidates are validated, then their provenance is <em>overwritten</em> from
     * the stored spans rather than trusted, so a reviewer can change what a candidate claims but not
     * which characters of the document it claims to come from.
     */
    @Transactional
    public ImportRead update(UUID importId, ImportUpdateRequest request) {
        ObjectNode submitted = request.validated();
        Document document = ownedImport(importId);
        if (document.getStatus() != DocumentStatus.needs_review) {
            throw ApplicationError.conflict(
                    "IMPORT_IMMUTABLE", "Only imports awaiting review can be edited.");
        }
        ObjectNode candidates = validateCandidates(document.getKind(), submitted);
        spanBinder.preserveSources(document, candidates);
        if (document.getKind() == DocumentKind.patient) {
            PatientCandidateAnnotator.Annotated annotated = annotator.annotate(candidates);
            candidates = annotated.json();
            document.setWarningsJson(
                    writeWarnings(mergeCatalogWarnings(document, annotated.warnings())));
        }
        document.setCandidatesJson(writeJson(candidates));
        documents.flush();
        return importRead(ownedImport(importId));
    }

    /**
     * Port of {@code reject_import()}.
     *
     * <p>Rejection is a state change, not a delete: the document, its spans and its candidates stay
     * on file, because "a human looked at this and declined it" is part of the audit trail.
     */
    @Transactional
    public void reject(UUID importId) {
        Document document = ownedImport(importId);
        if (document.getStatus() != DocumentStatus.needs_review) {
            throw ApplicationError.conflict(
                    "IMPORT_ALREADY_REVIEWED", "This import has already been reviewed.");
        }
        document.setStatus(DocumentStatus.rejected);
        documents.flush();
    }

    // ------------------------------------------------- shared with approval

    /**
     * Port of {@code _validate_candidates()}: validates against the contract for this document's kind
     * and returns the canonical JSON form.
     *
     * @throws ApplicationError 422 {@code IMPORT_REVIEW_INVALID} carrying one detail per failed field
     */
    public ObjectNode validateCandidates(DocumentKind kind, JsonNode candidates) {
        try {
            Object validated =
                    kind == DocumentKind.patient
                            ? validator.validatePatient(candidates)
                            : validator.validateTrial(candidates);
            return validator.toJson(validated);
        } catch (ImportCandidateValidationException exception) {
            throw new ApplicationError(
                    "IMPORT_REVIEW_INVALID",
                    "The edited candidates could not be validated.",
                    422,
                    null,
                    exception.getDetails());
        }
    }

    /**
     * Replaces the document's catalog verdicts with a freshly computed set, leaving every other
     * warning in place.
     *
     * <p>{@code [w for w in warnings_json if not w.startswith("Catalog review:")] + catalog_warnings}
     * - so a reviewer who corrects a unit sees that line disappear instead of accumulating a second
     * copy, while the parser's own warnings survive.
     */
    public List<String> mergeCatalogWarnings(Document document, List<String> catalogWarnings) {
        List<String> merged = new ArrayList<>();
        for (String warning : readWarnings(document.getWarningsJson())) {
            if (!warning.startsWith(CATALOG_REVIEW_PREFIX)) {
                merged.add(warning);
            }
        }
        merged.addAll(catalogWarnings);
        return merged;
    }

    /** The patient candidate contract, validated without the {@code IMPORT_REVIEW_INVALID} wrapper. */
    public PatientImportCandidates validatePatientCandidates(JsonNode candidates) {
        return validator.validatePatient(candidates);
    }

    /** The trial candidate contract, validated without the {@code IMPORT_REVIEW_INVALID} wrapper. */
    public TrialImportCandidates validateTrialCandidates(JsonNode candidates) {
        return validator.validateTrial(candidates);
    }

    /** Serialises a node for one of the document's {@code json} columns. */
    public String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("An import document could not be serialized", exception);
        }
    }

    /** Serialises the document-level warning list for {@code warnings_json}. */
    public String writeWarnings(List<String> warnings) {
        return writeJson(objectMapper.valueToTree(warnings));
    }

    /** Parses one of the document's {@code json} columns back into a tree. */
    public JsonNode readJson(String stored) {
        try {
            return objectMapper.readTree(stored);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("A stored import document could not be read", exception);
        }
    }

    // ---------------------------------------------------------------- helpers

    /**
     * The extraction attempt and its fallback.
     *
     * <p>The oversize check is raised as a {@link ProviderCallException} rather than reported to the
     * caller, exactly as Python does, so an input too large for the external provider takes the same
     * deterministic path as a provider outage and is recorded with the same
     * {@code provider_error} key.
     */
    private ExtractionRun extract(DocumentKind kind, ExtractedInput extracted) {
        try {
            if (extractor instanceof GroqExtractor
                    && PythonText.length(extracted.text()) > properties.getProviderMaxInputChars()) {
                throw new ProviderCallException(
                        "PROVIDER_INPUT_TOO_LARGE",
                        "The source exceeds the external provider limit.");
            }
            return extractor.extract(kind, extracted);
        } catch (ProviderCallException exception) {
            ExtractionRun deterministic = fallbackExtractor.extract(kind, extracted);
            List<String> warnings = new ArrayList<>();
            warnings.add("External extraction was unavailable; deterministic candidates are shown.");
            warnings.addAll(deterministic.warnings());
            // `validation_outcome` is already present, so overwriting it keeps its original position
            // and the two new keys land at the end - the key order Python's dict unpacking produced.
            Map<String, Object> metadata = new LinkedHashMap<>(deterministic.metadata());
            metadata.put("requested_provider", "groq");
            metadata.put("provider_error", exception.getCode());
            metadata.put("validation_outcome", "fallback");
            return new ExtractionRun(deterministic.candidates(), warnings, metadata);
        }
    }

    /**
     * Port of {@code _decode_pdf()}.
     *
     * <p>The explicit length check reproduces {@code validate=True}: Python rejects a payload whose
     * length is not a multiple of four, while Java's decoder would accept the truncated group.
     */
    private byte[] decodePdf(ImportAnalyzeRequest payload) {
        String declared = payload.mimeType();
        if (declared != null && !"application/pdf".equals(declared)) {
            throw new ApplicationError(
                    "IMPORT_WRONG_TYPE", "Only PDF files are accepted for PDF import.", 422);
        }
        String content = payload.contentBase64() == null ? "" : payload.contentBase64();
        if (content.length() % 4 != 0) {
            throw pdfMalformed();
        }
        try {
            return Base64.getDecoder().decode(content);
        } catch (IllegalArgumentException exception) {
            throw pdfMalformed();
        }
    }

    private static ApplicationError pdfMalformed() {
        return new ApplicationError(
                "PDF_MALFORMED", "The PDF payload is not valid base64.", 422);
    }

    /** {@code warnings_json} is a JSON array of strings on both sides. */
    private List<String> readWarnings(String stored) {
        List<String> warnings = new ArrayList<>();
        for (JsonNode warning : readJson(stored)) {
            warnings.add(warning.asText());
        }
        return warnings;
    }

    /** {@code hashlib.sha256(content).hexdigest()}. */
    private static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", exception);
        }
    }
}
