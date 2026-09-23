"""Unit tests for SHAP explainability pipeline (Phase R4)."""

from __future__ import annotations

import os
import tempfile
import numpy as np
import pandas as pd
import pytest

from dropout.explainability.shap.explainer import ShapDropoutExplainer
from dropout.models.xgboost.model import FEATURE_COLUMNS, XGBoostDropoutModel


@pytest.fixture
def trained_xgb_cohort():
    """Builds and fits an XGBoost model on synthetic clinical data."""
    rng = np.random.RandomState(42)
    n = 80
    data = {col: rng.normal(10.0, 2.0, n) for col in FEATURE_COLUMNS}
    df = pd.DataFrame(data)
    y = rng.choice([0, 1], size=n, p=[0.75, 0.25])

    model = XGBoostDropoutModel(n_estimators=10, max_depth=3, random_state=42)
    model.fit(df, y)
    return model, df


def test_shap_explainer_initialization(trained_xgb_cohort):
    """Validates that TreeExplainer initializes properly with expected base values."""
    model, df = trained_xgb_cohort
    explainer = ShapDropoutExplainer(model=model)

    assert explainer.explainer is not None
    assert explainer.feature_names == list(FEATURE_COLUMNS)
    assert isinstance(explainer.expected_value, float)


def test_shap_global_importance(trained_xgb_cohort):
    """Validates that global feature importances sum sensibly and match feature dimensions."""
    model, df = trained_xgb_cohort
    explainer = ShapDropoutExplainer(model=model)

    global_imp = explainer.compute_global_importance(df)
    assert len(global_imp) == len(FEATURE_COLUMNS)

    # Validate sort order (descending)
    scores = [item["mean_abs_shap"] for item in global_imp]
    assert scores == sorted(scores, reverse=True)
    assert all(s >= 0.0 for s in scores)


def test_shap_local_explanation(trained_xgb_cohort):
    """Validates local prediction explanation contains contribution direction, magnitude, and values."""
    model, df = trained_xgb_cohort
    explainer = ShapDropoutExplainer(model=model)

    sample_patient = df.iloc[0].to_dict()
    local_exp = explainer.explain_local_prediction(sample_patient, top_k=5)

    assert "base_value" in local_exp
    assert "predicted_probability" in local_exp
    assert len(local_exp["top_contributions"]) == 5
    assert len(local_exp["all_contributions"]) == len(FEATURE_COLUMNS)

    for item in local_exp["top_contributions"]:
        assert item["direction"] in ("INCREASES_RISK", "DECREASES_RISK", "NEUTRAL")
        assert item["abs_magnitude"] >= 0.0
        assert "value" in item
        assert "shap_value" in item


def test_shap_save_artifacts(trained_xgb_cohort):
    """Validates SHAP artifact export (JSON, CSV, and serialized explainer joblib)."""
    model, df = trained_xgb_cohort
    explainer = ShapDropoutExplainer(model=model)

    with tempfile.TemporaryDirectory() as tmp_dir:
        saved = explainer.save_artifacts(X_background=df, output_dir=tmp_dir)

        assert os.path.exists(saved["global_importance_json"])
        assert os.path.exists(saved["global_importance_csv"])
        assert os.path.exists(saved["sample_local_explanations"])
        assert os.path.exists(saved["explainer_path"])

        # Check CSV content
        imp_df = pd.read_csv(saved["global_importance_csv"])
        assert "feature" in imp_df.columns
        assert "mean_abs_shap" in imp_df.columns
        assert len(imp_df) == len(FEATURE_COLUMNS)
