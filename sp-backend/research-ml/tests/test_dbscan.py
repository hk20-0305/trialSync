"""Unit tests for CohortDBSCANClusterer (Phase R6)."""

from __future__ import annotations

import os
import tempfile
import numpy as np
import pandas as pd
import pytest

from cohort.clustering.dbscan.clusterer import CohortDBSCANClusterer


@pytest.fixture
def clustered_data():
    """Generates two dense synthetic Gaussian clusters plus outliers."""
    rng = np.random.RandomState(42)
    # Cluster 1: 30 samples around (0, 0, ...)
    c1 = rng.normal(0.0, 0.1, (30, 10))
    # Cluster 2: 30 samples around (5, 5, ...)
    c2 = rng.normal(5.0, 0.1, (30, 10))
    # Outliers: 5 samples far away
    outliers = rng.uniform(20.0, 50.0, (5, 10))

    X = np.vstack([c1, c2, outliers]).astype(np.float32)
    ids = [f"P-{i:03d}" for i in range(len(X))]
    return ids, X


def test_dbscan_clustering_and_noise_detection(clustered_data):
    """Validates cluster formation and outlier noise label (-1) detection."""
    ids, X = clustered_data
    clusterer = CohortDBSCANClusterer(eps=1.0, min_samples=5)
    labels = clusterer.fit_predict(ids, X)

    assert len(labels) == len(ids)
    assert -1 in labels  # Outliers detected as noise
    assert clusterer.metadata["n_clusters"] >= 2
    assert clusterer.metadata["n_noise_points"] >= 5


def test_dbscan_save_artifacts(clustered_data):
    """Validates cluster assignments and metadata persistence."""
    ids, X = clustered_data
    clusterer = CohortDBSCANClusterer(eps=1.0, min_samples=5)
    clusterer.fit_predict(ids, X)

    with tempfile.TemporaryDirectory() as tmp_dir:
        saved = clusterer.save_artifacts(tmp_dir, ids)

        assert os.path.exists(saved["clusters_csv"])
        assert os.path.exists(saved["metadata_json"])
        assert os.path.exists(saved["model_path"])

        df = pd.read_csv(saved["clusters_csv"])
        assert "participant_id" in df.columns
        assert "cluster_label" in df.columns
        assert "is_noise" in df.columns
        assert len(df) == len(ids)
