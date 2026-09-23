package com.trialsync.backend.controller;

import com.trialsync.backend.dto.trial.CriterionCreateRequest;
import com.trialsync.backend.dto.trial.CriterionRead;
import com.trialsync.backend.dto.trial.GuidedCriterionCreateRequest;
import com.trialsync.backend.dto.trial.UnsupportedCriterionCreateRequest;
import com.trialsync.backend.service.CriterionService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Port of the criterion routes of {@code trialsync.api.trials}.
 *
 * <p>A criterion is always addressed through its trial and version, which is what makes the
 * ownership check possible before the row is touched at all.
 */
@RestController
@RequestMapping("/api/v1/trials/{trialId}/versions/{versionId}")
public class CriterionController {

    private final CriterionService criterionService;

    public CriterionController(CriterionService criterionService) {
        this.criterionService = criterionService;
    }

    @PostMapping("/criteria")
    @ResponseStatus(HttpStatus.CREATED)
    public CriterionRead createCriterion(
            @PathVariable UUID trialId,
            @PathVariable UUID versionId,
            @Valid @RequestBody CriterionCreateRequest payload) {
        return criterionService.createCriterion(trialId, versionId, payload);
    }

    @PostMapping("/guided-criteria")
    @ResponseStatus(HttpStatus.CREATED)
    public CriterionRead createGuidedCriterion(
            @PathVariable UUID trialId,
            @PathVariable UUID versionId,
            @Valid @RequestBody GuidedCriterionCreateRequest payload) {
        return criterionService.createGuidedCriterion(trialId, versionId, payload);
    }

    @PostMapping("/unsupported-criteria")
    @ResponseStatus(HttpStatus.CREATED)
    public CriterionRead createUnsupportedCriterion(
            @PathVariable UUID trialId,
            @PathVariable UUID versionId,
            @Valid @RequestBody UnsupportedCriterionCreateRequest payload) {
        return criterionService.createUnsupportedCriterion(trialId, versionId, payload);
    }

    @PutMapping("/criteria/{criterionId}")
    public CriterionRead updateCriterion(
            @PathVariable UUID trialId,
            @PathVariable UUID versionId,
            @PathVariable UUID criterionId,
            @Valid @RequestBody CriterionCreateRequest payload) {
        return criterionService.updateCriterion(trialId, versionId, criterionId, payload);
    }

    @PutMapping("/guided-criteria/{criterionId}")
    public CriterionRead updateGuidedCriterion(
            @PathVariable UUID trialId,
            @PathVariable UUID versionId,
            @PathVariable UUID criterionId,
            @Valid @RequestBody GuidedCriterionCreateRequest payload) {
        return criterionService.updateGuidedCriterion(trialId, versionId, criterionId, payload);
    }

    @DeleteMapping("/criteria/{criterionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCriterion(
            @PathVariable UUID trialId, @PathVariable UUID versionId, @PathVariable UUID criterionId) {
        criterionService.deleteCriterion(trialId, versionId, criterionId);
    }
}
