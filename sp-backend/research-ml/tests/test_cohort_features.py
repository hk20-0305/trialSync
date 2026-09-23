"""Unit tests for CohortFeatureBuilder (Phase R6)."""

from __future__ import annotations

import os
import tempfile
import numpy as np
import pandas as pd
import pytest

from cohort.features.builder import COHORT_FEATURE_COLUMNS, CohortFeatureBuilder


@pytest.fixture
def dummy_cohort_df():
    """Builds a dummy DataFrame matching the 33 features plus identifiers."""
    rng = np.random.RandomState(42)
    n = 50
    data = {col: rng.normal(20.0, 5.0, n) for col in COHORT_FEATURE_COLUMNS}
    data["participant_id"] = [f"P-{i:03d}" for i in range(n)]
    data["enrollment_id"] = [f"E-{i:03d}" for i in range(n)]
    # Deliberately add target outcome columns to verify they are never included
    data["dropout_within_horizon"] = rng.choice([0, 1], size=n)
    data["dropout_day"] = rng.randint(15, 90, size=n)
    data["dropout_reason"] = "ADVERSE_EVENT"
    return pd.DataFrame(data)


def test_feature_matrix_generation_and_no_leakage(dummy_cohort_df):
    """Validates that feature builder extracts only valid features and omits outcome targets."""
    builder = CohortFeatureBuilder()
    p_ids, X_raw, X_scaled = builder.fit_transform(dummy_cohort_df)

    assert len(p_ids) == len(dummy_cohort_df)
    assert X_raw.shape == (50, len(COHORT_FEATURE_COLUMNS))
    assert X_scaled.shape == (50, len(COHORT_FEATURE_COLUMNS))

    # Validate zero outcome leakage
    for forbidden in ["dropout_within_horizon", "dropout_day", "dropout_reason"]:
        assert forbidden not in builder.feature_names

    # Check normalization: scaled features should have mean approx 0 and std approx 1
    means = np.mean(X_scaled, axis=0)
    stds = np.std(X_scaled, axis=0)
    np.testing.assert_allclose(means, 0.0, atol=1e-5)
    np.testing.assert_allclose(stds, 1.0, atol=1e-5)


def test_deterministic_feature_ordering(dummy_cohort_df):
    """Validates that column order is strictly identical to COHORT_FEATURE_COLUMNS."""
    # Permute columns in input DataFrame
    permuted_cols = list(dummy_cohort_df.columns)
    np.random.RandomState(99).shuffle(permuted_cols)
    permuted_df = dummy_cohort_df[permuted_cols]

    builder = CohortFeatureBuilder()
    p_ids, _, X_scaled = builder.fit_transform(permuted_df)

    assert builder.feature_names == list(COHORT_FEATURE_COLUMNS)
    assert p_ids == dummy_cohort_df["participant_id"].tolist()


def test_missing_value_handling():
    """Validates that missing values are handled explicitly without errors."""
    rng = np.random.RandomState(42)
    n = 20
    data = {col: rng.normal(10.0, 2.0, n) for col in COHORT_FEATURE_COLUMNS}
    data["participant_id"] = [f"P-{i:03d}" for i in range(n)]
    df = pd.DataFrame(data)
    # Introduce NaNs
    df.loc[0, "alt_latest_pre_cutoff"] = np.nan
    df.loc[3, "frailty_index"] = np.nan

    builder = CohortFeatureBuilder()
    _, X_raw, X_scaled = builder.fit_transform(df)

    assert not np.isnan(X_raw).any()
    assert not np.isnan(X_scaled).any()


def test_save_and_load_artifacts(dummy_cohort_df):
    """Validates artifact export (CSV, scaler joblib, metadata JSON)."""
    builder = CohortFeatureBuilder()
    p_ids, _, X_scaled = builder.fit_transform(dummy_cohort_df)

    with tempfile.TemporaryDirectory() as tmp_dir:
        saved = builder.save_artifacts(tmp_dir, p_ids, X_scaled)

        assert os.path.exists(saved["features_csv"])
        assert os.path.exists(saved["scaler_path"])
        assert os.path.exists(saved["metadata_path"])

        saved_df = pd.read_csv(saved["features_csv"])
        assert saved_df.shape == (50, len(COHORT_FEATURE_COLUMNS) + 1)
        assert "participant_id" in saved_df.columns
