package com.trialsync.backend.controller;

import com.trialsync.backend.dto.trial.TrialCreateRequest;
import com.trialsync.backend.dto.trial.TrialRead;
import com.trialsync.backend.dto.trial.TrialUpdateRequest;
import com.trialsync.backend.dto.trial.VersionCreateRequest;
import com.trialsync.backend.dto.trial.VersionRead;
import com.trialsync.backend.service.TrialService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Port of the trial and version routes of {@code trialsync.api.trials}.
 *
 * <p>Nothing here decides anything: the routes bind, delegate, and pick the status code, so the
 * ownership and lifecycle rules live in one place in {@link TrialService} and cannot be bypassed by
 * a future caller that skips the controller.
 */
@RestController
@RequestMapping("/api/v1/trials")
public class TrialController {

    private final TrialService trialService;

    public TrialController(TrialService trialService) {
        this.trialService = trialService;
    }

    @GetMapping
    public List<TrialRead> listTrials() {
        return trialService.listTrials();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrialRead createTrial(@Valid @RequestBody TrialCreateRequest payload) {
        return trialService.createTrial(payload);
    }

    @GetMapping("/{trialId}")
    public TrialRead getTrial(@PathVariable UUID trialId) {
        return trialService.getTrial(trialId);
    }

    @PatchMapping("/{trialId}")
    public TrialRead updateTrial(
            @PathVariable UUID trialId, @Valid @RequestBody TrialUpdateRequest payload) {
        return trialService.updateTrial(trialId, payload);
    }

    @DeleteMapping("/{trialId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTrial(@PathVariable UUID trialId) {
        trialService.deleteTrial(trialId);
    }

    @PostMapping("/{trialId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public VersionRead createVersion(
            @PathVariable UUID trialId, @Valid @RequestBody VersionCreateRequest payload) {
        return trialService.createVersion(trialId, payload);
    }

    /** Opens the next draft as a copy of the newest revision. Takes no body. */
    @PostMapping("/{trialId}/versions/draft")
    @ResponseStatus(HttpStatus.CREATED)
    public VersionRead createGuidedDraft(@PathVariable UUID trialId) {
        return trialService.createGuidedDraft(trialId);
    }

    @PutMapping("/{trialId}/versions/{versionId}")
    public VersionRead updateVersion(
            @PathVariable UUID trialId,
            @PathVariable UUID versionId,
            @Valid @RequestBody VersionCreateRequest payload) {
        return trialService.updateVersion(trialId, versionId, payload);
    }

    @DeleteMapping("/{trialId}/versions/{versionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVersion(@PathVariable UUID trialId, @PathVariable UUID versionId) {
        trialService.deleteVersion(trialId, versionId);
    }
}
