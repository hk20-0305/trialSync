package com.trialsync.backend.domain.model;

import java.util.List;

/**
 * The frozen trial version a patient is screened against.
 *
 * <p>Port of {@code trialsync.domain.types.ApprovedTrialVersion}. Only approved versions ever reach
 * the engine; the service layer enforces that.
 */
public record ApprovedTrialVersion(
        String id, String version, List<Criterion> criteria, String dslVersion) {

    public static final String DEFAULT_DSL_VERSION = "1.0";

    public ApprovedTrialVersion {
        criteria = criteria == null ? List.of() : List.copyOf(criteria);
        dslVersion = dslVersion == null ? DEFAULT_DSL_VERSION : dslVersion;
    }

    public ApprovedTrialVersion(String id, String version, List<Criterion> criteria) {
        this(id, version, criteria, DEFAULT_DSL_VERSION);
    }
}
