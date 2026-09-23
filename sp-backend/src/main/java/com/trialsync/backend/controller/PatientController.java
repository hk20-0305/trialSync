package com.trialsync.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.trialsync.backend.dto.patient.FactResponse;
import com.trialsync.backend.dto.patient.PatientChangeEventResponse;
import com.trialsync.backend.dto.patient.PatientCreateRequest;
import com.trialsync.backend.dto.patient.PatientFactCreateRequest;
import com.trialsync.backend.dto.patient.PatientFactUpdateRequest;
import com.trialsync.backend.dto.patient.PatientFactVoidRequest;
import com.trialsync.backend.dto.patient.PatientResponse;
import com.trialsync.backend.dto.patient.PatientUpdateRequest;
import com.trialsync.backend.dto.patient.UnsupportedDetailCreateRequest;
import com.trialsync.backend.dto.patient.UnsupportedDetailResponse;
import com.trialsync.backend.dto.patient.UnsupportedDetailUpdateRequest;
import com.trialsync.backend.service.PatientFactService;
import com.trialsync.backend.service.PatientService;

import jakarta.validation.Valid;

/**
 * Port of {@code trialsync.api.patients}: the synthetic patient, its clinical details and its
 * review items.
 *
 * <p>The routes bind and delegate. Ownership, staleness, catalog validation and the change trail all
 * live in {@link PatientService} and {@link PatientFactService}, so no rule here can be bypassed by
 * a caller that reaches the services another way.
 *
 * <p>Two bindings are worth noting. The fact removal is a {@code DELETE} that carries a body,
 * because a removal is not permitted without a reason and the reason is recorded on the audit trail.
 * And the restore routes take no body at all: a restore reinstates what was recorded rather than
 * re-authoring it.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    private final PatientService patientService;
    private final PatientFactService patientFactService;

    public PatientController(PatientService patientService, PatientFactService patientFactService) {
        this.patientService = patientService;
        this.patientFactService = patientFactService;
    }

    @GetMapping
    public List<PatientResponse> listPatients() {
        return patientService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PatientResponse createPatient(@Valid @RequestBody PatientCreateRequest payload) {
        return patientService.create(payload);
    }

    @GetMapping("/{patientId}")
    public PatientResponse getPatient(@PathVariable UUID patientId) {
        return patientService.get(patientId);
    }

    /** The bounded immutable change history for one owned patient. */
    @GetMapping("/{patientId}/activity")
    public List<PatientChangeEventResponse> getPatientActivity(@PathVariable UUID patientId) {
        return patientService.activity(patientId);
    }

    @PatchMapping("/{patientId}")
    public PatientResponse updatePatient(
            @PathVariable UUID patientId, @Valid @RequestBody PatientUpdateRequest payload) {
        return patientService.update(patientId, payload);
    }

    @DeleteMapping("/{patientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePatient(@PathVariable UUID patientId) {
        patientService.delete(patientId);
    }

    @PostMapping("/{patientId}/facts")
    @ResponseStatus(HttpStatus.CREATED)
    public FactResponse createFact(
            @PathVariable UUID patientId, @Valid @RequestBody PatientFactCreateRequest payload) {
        return patientFactService.create(patientId, payload);
    }

    @PatchMapping("/{patientId}/facts/{factId}")
    public FactResponse updateFact(
            @PathVariable UUID patientId,
            @PathVariable UUID factId,
            @Valid @RequestBody PatientFactUpdateRequest payload) {
        return patientFactService.update(patientId, factId, payload);
    }

    /**
     * Removes a clinical detail.
     *
     * <p>Takes a body, which is unusual for a {@code DELETE} and deliberate: the removal reason is
     * mandatory and is kept on both the row and the trail entry, so the detail is voided rather
     * than deleted and the removal can be reviewed and undone.
     */
    @DeleteMapping("/{patientId}/facts/{factId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFact(
            @PathVariable UUID patientId,
            @PathVariable UUID factId,
            @Valid @RequestBody PatientFactVoidRequest payload) {
        patientFactService.voidFact(patientId, factId, payload);
    }

    /** Brings a removed detail back. Takes no body. */
    @PostMapping("/{patientId}/facts/{factId}/restore")
    public FactResponse restoreFact(@PathVariable UUID patientId, @PathVariable UUID factId) {
        return patientFactService.restore(patientId, factId);
    }

    @PostMapping("/{patientId}/unsupported-details")
    @ResponseStatus(HttpStatus.CREATED)
    public UnsupportedDetailResponse createUnsupportedDetail(
            @PathVariable UUID patientId,
            @Valid @RequestBody UnsupportedDetailCreateRequest payload) {
        return patientService.createUnsupportedDetail(patientId, payload);
    }

    @PatchMapping("/{patientId}/unsupported-details/{detailId}")
    public UnsupportedDetailResponse updateUnsupportedDetail(
            @PathVariable UUID patientId,
            @PathVariable UUID detailId,
            @Valid @RequestBody UnsupportedDetailUpdateRequest payload) {
        return patientService.updateUnsupportedDetail(patientId, detailId, payload);
    }

    @DeleteMapping("/{patientId}/unsupported-details/{detailId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUnsupportedDetail(
            @PathVariable UUID patientId, @PathVariable UUID detailId) {
        patientService.deleteUnsupportedDetail(patientId, detailId);
    }
}
