package com.trialsync.backend.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.trialsync.backend.dto.imports.ImportAnalyzeRequest;
import com.trialsync.backend.dto.imports.ImportApprovalRead;
import com.trialsync.backend.dto.imports.ImportApproveRequest;
import com.trialsync.backend.dto.imports.ImportRead;
import com.trialsync.backend.dto.imports.ImportUpdateRequest;
import com.trialsync.backend.service.ImportApprovalService;
import com.trialsync.backend.service.ImportService;

/**
 * Port of the routes of {@code trialsync.api.imports}.
 *
 * <p>The routes bind, delegate and pick the status code. Ownership, the review state machine and
 * every database side effect live in {@link ImportService} and {@link ImportApprovalService}, so no
 * future caller can reach an import without passing the same guards.
 *
 * <p>The request bodies validate themselves inside the service rather than through
 * {@code @Valid}: both are open JSON on the Python side and their bounds were enforced by Pydantic
 * model validators, so keeping the checks in the DTOs reproduces the original error envelope
 * exactly - see {@code ImportAnalyzeRequest.validated()}.
 */
@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    private final ImportService importService;
    private final ImportApprovalService approvalService;

    public ImportController(ImportService importService, ImportApprovalService approvalService) {
        this.importService = importService;
        this.approvalService = approvalService;
    }

    /** Extracts review candidates from pasted text or an uploaded PDF. Writes no clinical record. */
    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportRead analyzeImport(@RequestBody ImportAnalyzeRequest payload) {
        return importService.analyze(payload);
    }

    @GetMapping("/{importId}")
    public ImportRead getImport(@PathVariable UUID importId) {
        return importService.get(importId);
    }

    /** Replaces the candidate document with the reviewer's edit. Provenance is not editable. */
    @PutMapping("/{importId}")
    public ImportRead updateImport(
            @PathVariable UUID importId, @RequestBody ImportUpdateRequest payload) {
        return importService.update(importId, payload);
    }

    /** Turns the reviewed candidates into a patient or a trial. */
    @PostMapping("/{importId}/approve")
    public ImportApprovalRead approveImport(
            @PathVariable UUID importId, @RequestBody ImportApproveRequest payload) {
        return approvalService.approve(importId, payload);
    }

    /** Declines the import. The document is retained as {@code rejected}, not deleted. */
    @DeleteMapping("/{importId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectImport(@PathVariable UUID importId) {
        importService.reject(importId);
    }
}
