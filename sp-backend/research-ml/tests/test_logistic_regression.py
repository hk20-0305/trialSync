"""Unit tests for Logistic Regression model (Phase R4)."""

from __future__ import annotations

import os
import tempfile
import numpy as np
import pandas as pd
import pytest

from dropout.evaluation.metrics import compute_binary_classification_metrics
from dropout.models.logistic_regression.model import (
    FEATURE_COLUMNS,
    LogisticRegressionDropoutModel,
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


def test_logistic_regression_fit_predict(dummy_dataset):
    """Validates that LogisticRegression fits, produces valid probabilities and predictions."""
    X, y = dummy_dataset
    model = LogisticRegressionDropoutModel(c_param=1.0, random_state=42)
    assert not model.is_fitted

    model.fit(X, y)
    assert model.is_fitted
    assert len(model.training_metadata["coefficients"]) == len(FEATURE_COLUMNS)

    probas = model.predict_proba(X)
    assert len(probas) == len(X)
    assert np.all((probas >= 0.0) & (probas <= 1.0))

    preds = model.predict(X, threshold=0.5)
    assert len(preds) == len(X)
    assert set(preds).issubset({0, 1})


def test_logistic_regression_save_load(dummy_dataset):
    """Validates model serialization, deserialization, and output parity."""
    X, y = dummy_dataset
    model = LogisticRegressionDropoutModel(c_param=1.0, random_state=42)
    model.fit(X, y)
    orig_probas = model.predict_proba(X)

    with tempfile.TemporaryDirectory() as tmp_dir:
        saved = model.save(tmp_dir, metrics={"test_auroc": 0.85})
        assert os.path.exists(saved["model_path"])
        assert os.path.exists(saved["metadata_path"])

        loaded_model = LogisticRegressionDropoutModel.load(tmp_dir)
        assert loaded_model.is_fitted
        loaded_probas = loaded_model.predict_proba(X)

        np.testing.assert_allclose(orig_probas, loaded_probas, rtol=1e-5)


def test_logistic_regression_missing_column_error():
    """Validates that missing required feature columns raises ValueError."""
    model = LogisticRegressionDropoutModel()
    incomplete_df = pd.DataFrame({"age": [50], "is_female": [1]})
    with pytest.raises(ValueError, match="Missing required feature columns"):
        model.fit(incomplete_df, np.array([1]))
