package com.trialsync.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.dto.trial.TrialCreateRequest;
import com.trialsync.backend.dto.trial.TrialRead;
import com.trialsync.backend.dto.trial.TrialUpdateRequest;
import com.trialsync.backend.dto.trial.VersionCreateRequest;
import com.trialsync.backend.dto.trial.VersionRead;
import com.trialsync.backend.entity.Criterion;
import com.trialsync.backend.entity.Trial;
import com.trialsync.backend.entity.TrialVersion;
import com.trialsync.backend.entity.enums.VersionStatus;
import com.trialsync.backend.repository.CriterionRepository;
import com.trialsync.backend.repository.TrialRepository;
import com.trialsync.backend.repository.TrialVersionRepository;
import com.trialsync.backend.security.SecurityContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Port of the trial and version half of {@code trialsync.api.trials}.
 *
 * <p>Ownership is enforced by the lookup itself rather than by a separate check: a trial that
 * belongs to someone else is reported as missing, so the API cannot be used to discover which
 * registry ids exist. {@link #ownedTrial} and {@link #ownedVersion} are the only entry points to a
 * row and are shared with {@link CriterionService}.
 *
 * <p>Uniqueness is likewise left to the database. Every conflict below is a caught constraint
 * violation rather than a pre-flight query, which is both a round trip cheaper and safe under
 * concurrency. {@code DataIntegrityViolationException} is deliberately not handled globally - it
 * would answer 500 - so each site that can provoke one catches it and re-raises the code the client
 * contract names.
 */
@Service
public class TrialService {

    private final TrialRepository trials;
    private final TrialVersionRepository versions;
    private final CriterionRepository criteria;
    private final ObjectMapper objectMapper;

    public TrialService(
            TrialRepository trials,
            TrialVersionRepository versions,
            CriterionRepository criteria,
            ObjectMapper objectMapper) {
        this.trials = trials;
        this.versions = versions;
        this.criteria = criteria;
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- lookups

    /** Port of {@code owned_trial()}. */
    public Trial ownedTrial(UUID trialId) {
        UUID ownerId = SecurityContext.require().getId();
        return trials.findByIdAndOwnerId(trialId, ownerId)
                .orElseThrow(() -> ApplicationError.notFound("TRIAL_NOT_FOUND", "Trial was not found."));
    }

    /**
     * Port of {@code owned_version()}.
     *
     * <p>The parent trial is resolved first and the order matters: asking for a version under a trial
     * the caller does not own answers {@code TRIAL_NOT_FOUND}, not {@code TRIAL_VERSION_NOT_FOUND}.
     */
    public TrialVersion ownedVersion(UUID trialId, UUID versionId) {
        ownedTrial(trialId);
        return versions.findByIdAndTrialId(versionId, trialId)
                .orElseThrow(
                        () ->
                                ApplicationError.notFound(
                                        "TRIAL_VERSION_NOT_FOUND", "Trial version was not found."));
    }

    /** Port of {@code require_draft()}: an approved version is frozen. */
    public void requireDraft(TrialVersion version) {
        if (version.getStatus() != VersionStatus.draft) {
            throw ApplicationError.conflict(
                    "APPROVED_VERSION_IMMUTABLE", "Approved trial versions cannot be changed.");
        }
    }

    /**
     * Port of {@code require_approvable()}: approval is the point where the review has to be
     * complete, so the version needs at least one criterion and every one of them needs a
     * deterministic rule. The two failures share a code but not a message, and the client shows the
     * message, so both are reproduced exactly.
     */
    public void requireApprovable(TrialVersion version) {
        List<Criterion> rows = version.getCriteria();
        if (rows.isEmpty()) {
            throw ApplicationError.unprocessable(
                    "TRIAL_VERSION_REVIEW_INCOMPLETE",
                    "Add at least one reviewed criterion before approval.",
                    null);
        }
        for (Criterion criterion : rows) {
            if (!isNonEmptyRuleObject(criterion.getNormalizedRule())) {
                throw ApplicationError.unprocessable(
                        "TRIAL_VERSION_REVIEW_INCOMPLETE",
                        "Every criterion needs a deterministic rule before approval.",
                        null);
            }
        }
    }

    /**
     * Python tested {@code isinstance(rule, dict) and rule}, so a null rule, a JSON array, a scalar
     * and an empty object all count as unreviewed.
     */
    private boolean isNonEmptyRuleObject(String storedRule) {
        if (storedRule == null) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(storedRule);
            return node.isObject() && node.size() > 0;
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    // ----------------------------------------------------------------- trials

    /** Port of {@code list_trials()}. */
    @Transactional(readOnly = true)
    public List<TrialRead> listTrials() {
        UUID ownerId = SecurityContext.require().getId();
        return trials.findTop100ByOwnerIdOrderByUpdatedAtDesc(ownerId).stream()
                .map(TrialRead::of)
                .toList();
    }

    /**
     * Port of {@code create_trial()}.
     *
     * <p>A trial registered without an identifier gets a synthetic one so the column stays non-null
     * and the record is still citable; the generated value is unique per owner by the same index that
     * guards a supplied one.
     */
    @Transactional
    public TrialRead createTrial(TrialCreateRequest payload) {
        UUID ownerId = SecurityContext.require().getId();
        Trial trial =
                new Trial(
                        ownerId,
                        payload.registryId() == null ? syntheticRegistryId() : payload.registryId(),
                        payload.title(),
                        payload.condition());
        trial.setPhase(payload.phase());
        try {
            trial = trials.saveAndFlush(trial);
        } catch (DataIntegrityViolationException exception) {
            throw registryIdConflict();
        }
        // A trial is created with no versions, so the collection is known to be empty and reading it
        // back would only cost a query.
        return TrialRead.of(trial, List.of());
    }

    /** Port of {@code get_trial()}. */
    @Transactional(readOnly = true)
    public TrialRead getTrial(UUID trialId) {
        return TrialRead.of(ownedTrial(trialId));
    }

    /**
     * Port of {@code update_trial()}.
     *
     * <p>Only the keys present in the body are applied, which is what {@code exclude_unset=True} did:
     * an omitted field keeps its stored value while an explicit {@code null} clears it. Sending
     * {@code null} for a non-nullable column therefore fails at the database and, exactly as in
     * Python, is reported through the same registry-id conflict this block catches.
     */
    @Transactional
    public TrialRead updateTrial(UUID trialId, TrialUpdateRequest payload) {
        Trial trial = ownedTrial(trialId);
        if (payload.isRegistryIdPresent()) {
            trial.setRegistryId(payload.getRegistryId());
        }
        if (payload.isTitlePresent()) {
            trial.setTitle(payload.getTitle());
        }
        if (payload.isConditionPresent()) {
            trial.setCondition(payload.getCondition());
        }
        if (payload.isPhasePresent()) {
            trial.setPhase(payload.getPhase());
        }
        try {
            trials.flush();
        } catch (DataIntegrityViolationException exception) {
            throw registryIdConflict();
        }
        return TrialRead.of(trial);
    }

    /**
     * Port of {@code delete_trial()}.
     *
     * <p>Only the trial row is deleted; {@code trial_versions} and {@code criteria} go with it
     * through {@code ON DELETE CASCADE}. That cascade is also what enforces the rule here - a version
     * cited by a saved screening is protected by {@code screenings_trial_version_id_fkey ON DELETE
     * RESTRICT}, so the cascade fails and the trial survives, which is the whole point: an audit
     * trail cannot be erased by deleting the protocol it refers to.
     */
    @Transactional
    public void deleteTrial(UUID trialId) {
        Trial trial = ownedTrial(trialId);
        trials.delete(trial);
        try {
            trials.flush();
        } catch (DataIntegrityViolationException exception) {
            throw ApplicationError.conflict(
                    "TRIAL_HAS_SCREENING_HISTORY", "Trials used by saved screenings cannot be deleted.");
        }
    }

    // --------------------------------------------------------------- versions

    /**
     * Port of {@code create_version()}.
     *
     * <p>The status is taken from the body without a completeness check, matching Python: this
     * endpoint is the explicit "record this revision as it is" path, and the guarded transition is
     * {@link #updateVersion}.
     */
    @Transactional
    public VersionRead createVersion(UUID trialId, VersionCreateRequest payload) {
        ownedTrial(trialId);
        TrialVersion version = new TrialVersion(trialId, payload.getVersion());
        version.setStatus(payload.getStatus());
        version.setSourceText(payload.getSourceText());
        try {
            version = versions.saveAndFlush(version);
        } catch (DataIntegrityViolationException exception) {
            throw versionConflict();
        }
        return VersionRead.of(version, List.of());
    }

    /**
     * Port of {@code create_guided_draft()}: opens the next revision as a copy of the newest one so a
     * reviewer edits a draft instead of the approved version screenings already cite.
     *
     * <p>At most one draft may be open at a time, and the conflict carries the existing draft's id in
     * {@code details} so the client can navigate straight to it rather than dead-ending.
     */
    @Transactional
    public VersionRead createGuidedDraft(UUID trialId) {
        Trial trial = ownedTrial(trialId);
        List<TrialVersion> existing = trial.getVersions();

        TrialVersion openDraft =
                existing.stream()
                        .filter(candidate -> candidate.getStatus() == VersionStatus.draft)
                        .findFirst()
                        .orElse(null);
        if (openDraft != null) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("version_id", openDraft.getId().toString());
            throw new ApplicationError(
                    "TRIAL_DRAFT_EXISTS",
                    "This trial already has an editable draft.",
                    409,
                    null,
                    List.of(detail));
        }

        // The collection is ordered by version number, so the last element is the newest revision.
        TrialVersion latest = existing.isEmpty() ? null : existing.get(existing.size() - 1);
        TrialVersion draft =
                new TrialVersion(trialId, latest == null ? 1 : latest.getVersion() + 1);
        draft.setStatus(VersionStatus.draft);
        draft.setSourceText(latest == null ? null : latest.getSourceText());
        draft = versions.saveAndFlush(draft);

        List<Criterion> copies = new ArrayList<>();
        if (latest != null) {
            for (Criterion source : latest.getCriteria()) {
                Criterion copy =
                        new Criterion(
                                draft.getId(),
                                source.getKind(),
                                source.getOrder(),
                                source.getSourceText());
                copy.setNormalizedRule(source.getNormalizedRule());
                copy.setRequired(source.isRequired());
                copies.add(copy);
            }
            copies = criteria.saveAll(copies);
            criteria.flush();
        }
        // Built from the rows just written rather than from the draft's mapped collection, which is a
        // separate lazy association that would not see them without a reload.
        return VersionRead.of(draft, copies);
    }

    /**
     * Port of {@code update_version()}.
     *
     * <p>This is the approval transition. The draft guard runs first, so approving twice is a
     * conflict rather than a silent no-op, and the completeness guard runs before anything is
     * written, so a rejected approval leaves the draft untouched.
     *
     * <p>The body replaces the version wholesale - the Python handler dumped the model without
     * {@code exclude_unset}, so an omitted {@code source_text} clears the stored text and an omitted
     * {@code status} resets it to draft. That is a PUT, and it is preserved rather than quietly
     * upgraded to merge semantics.
     */
    @Transactional
    public VersionRead updateVersion(UUID trialId, UUID versionId, VersionCreateRequest payload) {
        TrialVersion version = ownedVersion(trialId, versionId);
        requireDraft(version);
        if (payload.getStatus() == VersionStatus.approved) {
            requireApprovable(version);
        }
        version.setVersion(payload.getVersion());
        version.setStatus(payload.getStatus());
        version.setSourceText(payload.getSourceText());
        try {
            versions.flush();
        } catch (DataIntegrityViolationException exception) {
            throw versionConflict();
        }
        return VersionRead.of(version);
    }

    /**
     * Port of {@code delete_version()}.
     *
     * <p>Restricted to drafts, which is also why no constraint violation is caught here: a screening
     * can only cite an approved version, so a draft can never be the protected side of the
     * {@code ON DELETE RESTRICT} that guards {@link #deleteTrial}.
     */
    @Transactional
    public void deleteVersion(UUID trialId, UUID versionId) {
        TrialVersion version = ownedVersion(trialId, versionId);
        requireDraft(version);
        versions.delete(version);
        versions.flush();
    }

    // ---------------------------------------------------------------- helpers

    /** {@code f"SYN-TRIAL-{uuid.uuid4().hex[:10].upper()}"}. */
    private String syntheticRegistryId() {
        String hex = UUID.randomUUID().toString().replace("-", "");
        return "SYN-TRIAL-" + hex.substring(0, 10).toUpperCase(Locale.ROOT);
    }

    private ApplicationError registryIdConflict() {
        return new ApplicationError(
                "TRIAL_REGISTRY_ID_EXISTS", "This registry ID is already in use.", 409, "registry_id");
    }

    private ApplicationError versionConflict() {
        return new ApplicationError(
                "TRIAL_VERSION_EXISTS", "This trial version already exists.", 409, "version");
    }
}
