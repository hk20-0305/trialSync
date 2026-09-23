"""Unit tests for XGBoost model (Phase R4)."""

from __future__ import annotations

import os
import tempfile
import numpy as np
import pandas as pd
import pytest

from dropout.models.xgboost.model import (
    FEATURE_COLUMNS,
    XGBoostDropoutModel,
)


@pytest.fixture
def dummy_dataset():
    """Generates synthetic dataframe matching FEATURE_COLUMNS for unit testing."""
    rng = np.random.RandomState(42)
    n = 100
    data = {}
    for col in FEATURE_COLUMNS:
        data[col] = rng.normal(10.0, 2.0, n)
    df = pd.DataFrame(data)
    y = rng.choice([0, 1], size=n, p=[0.75, 0.25])
    return df, y


def test_xgboost_fit_predict(dummy_dataset):
    """Validates that XGBoost fits, computes feature importances, and generates predictions."""
    X, y = dummy_dataset
    model = XGBoostDropoutModel(n_estimators=10, max_depth=3, random_state=42)
    assert not model.is_fitted

    model.fit(X, y)
    assert model.is_fitted
    assert len(model.training_metadata["feature_importances"]) == len(FEATURE_COLUMNS)

    probas = model.predict_proba(X)
    assert len(probas) == len(X)
    assert np.all((probas >= 0.0) & (probas <= 1.0))

    preds = model.predict(X, threshold=0.5)
    assert len(preds) == len(X)
    assert set(preds).issubset({0, 1})


def test_xgboost_imbalance_handling(dummy_dataset):
    """Validates that scale_pos_weight is correctly computed from training imbalance."""
    X, y = dummy_dataset
    model = XGBoostDropoutModel(n_estimators=5, random_state=42)
    model.fit(X, y)

    neg_count = int(np.sum(y == 0))
    pos_count = int(np.sum(y == 1))
    expected_scale = neg_count / max(1, pos_count)

    assert pytest.approx(model.training_metadata["scale_pos_weight"], rel=1e-3) == expected_scale


def test_xgboost_save_load(dummy_dataset):
    """Validates model serialization, deserialization, and output parity."""
    X, y = dummy_dataset
    model = XGBoostDropoutModel(n_estimators=10, max_depth=3, random_state=42)
    model.fit(X, y)
    orig_probas = model.predict_proba(X)

    with tempfile.TemporaryDirectory() as tmp_dir:
        saved = model.save(tmp_dir, metrics={"test_auroc": 0.88})
        assert os.path.exists(saved["model_joblib_path"])
        assert os.path.exists(saved["model_json_path"])
        assert os.path.exists(saved["metadata_path"])

        loaded_model = XGBoostDropoutModel.load(tmp_dir)
        assert loaded_model.is_fitted
        loaded_probas = loaded_model.predict_proba(X)

        np.testing.assert_allclose(orig_probas, loaded_probas, rtol=1e-4)
