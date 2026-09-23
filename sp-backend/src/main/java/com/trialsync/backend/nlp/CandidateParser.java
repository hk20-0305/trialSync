package com.trialsync.backend.nlp;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trialsync.backend.entity.enums.DocumentKind;
import com.trialsync.backend.imports.ExtractedInput;

/**
 * The seam between this package and the deterministic import parser, mirroring the Python import
 * {@code from trialsync.imports.parser import extract_patient_candidates, extract_trial_candidates}
 * and the {@code PatientImportCandidates}/{@code TrialImportCandidates} validation step at the end
 * of {@code _convert_payload}.
 *
 * <p>It exists as an interface rather than a direct call so the extraction providers here can be
 * exercised without the PDF and OCR machinery behind them, and so the two layers stay independently
 * testable. The contract is deliberately narrow: parse a document into candidates, or re-validate
 * candidates that came from somewhere less trustworthy.
 */
public interface CandidateParser {

    /** {@code extract_patient_candidates(extracted)}. */
    CandidateExtraction extractPatientCandidates(ExtractedInput extracted);

    /** {@code extract_trial_candidates(extracted)}. */
    CandidateExtraction extractTrialCandidates(ExtractedInput extracted);

    /**
     * Re-validates a candidate document against the import contract and returns its canonical JSON
     * form - the Java equivalent of
     * {@code PatientImportCandidates.model_validate(result).model_dump(mode="json")}.
     *
     * <p>This is the final gate on provider output: candidates that do not satisfy the same model
     * the reviewed-import API accepts are rejected here rather than shown to a reviewer.
     *
     * @throws CandidateValidationException when the payload does not satisfy the contract
     */
    ObjectNode validateCandidates(DocumentKind kind, ObjectNode candidates);
}
