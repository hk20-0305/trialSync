"""Unit tests for CohortPCAReducer (Phase R6)."""

from __future__ import annotations

import os
import tempfile
import numpy as np
import pandas as pd
import pytest

from cohort.dimensionality_reduction.pca.reducer import CohortPCAReducer


@pytest.fixture
def sample_feature_matrix():
    """Generates synthetic feature matrix."""
    rng = np.random.RandomState(42)
    n = 60
    d = 15
    X = rng.normal(0.0, 1.0, (n, d)).astype(np.float32)
    ids = [f"P-{i:03d}" for i in range(n)]
    labels = rng.choice([0, 1, -1], size=n)
    return ids, labels, X


def test_pca_fit_transform_shape_and_variance(sample_feature_matrix):
    """Validates 2D projection shape and explained variance ratio calculations."""
    ids, labels, X = sample_feature_matrix
    reducer = CohortPCAReducer(n_components=2, random_state=42)
    proj_df = reducer.fit_transform(X, ids, cluster_labels=labels.tolist())

    assert len(proj_df) == len(ids)
    assert set(proj_df.columns).issuperset({"participant_id", "pc1", "pc2", "cluster_label", "is_noise"})

    # Check explained variance ratios
    meta = reducer.metadata
    assert "PC1" in meta["explained_variance_ratio"]
    assert "PC2" in meta["explained_variance_ratio"]
    assert 0.0 < meta["cumulative_explained_variance"] <= 1.0
    assert meta["explained_variance_ratio"]["PC1"] >= meta["explained_variance_ratio"]["PC2"]


def test_pca_save_artifacts(sample_feature_matrix):
    """Validates saving PCA projection coordinates and model artifact."""
    ids, labels, X = sample_feature_matrix
    reducer = CohortPCAReducer(n_components=2, random_state=42)
    proj_df = reducer.fit_transform(X, ids, cluster_labels=labels.tolist())

    with tempfile.TemporaryDirectory() as tmp_dir:
        saved = reducer.save_artifacts(tmp_dir, proj_df)

        assert os.path.exists(saved["projection_csv"])
        assert os.path.exists(saved["metadata_json"])
        assert os.path.exists(saved["model_path"])

        loaded_df = pd.read_csv(saved["projection_csv"])
        assert len(loaded_df) == len(ids)
        assert "pc1" in loaded_df.columns
        assert "pc2" in loaded_df.columns
