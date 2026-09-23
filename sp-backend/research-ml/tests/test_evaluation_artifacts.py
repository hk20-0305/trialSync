"""Unit tests for model evaluation and artifact generation (Phase R4)."""

from __future__ import annotations

import json
import os
import tempfile
import numpy as np
import pandas as pd
import pytest

from dropout.evaluation.evaluate import (
    compute_calibration_details,
    evaluate_models_and_save_artifacts,
)
from dropout.models.logistic_regression.model import (
    FEATURE_COLUMNS,
    LogisticRegressionDropoutModel,
)
from dropout.models.xgboost.model import XGBoostDropoutModel


def test_calibration_details_computation():
    """Validates calibration details computing brier score and expected calibration error."""
    y_true = np.array([0, 0, 0, 1, 1, 1, 0, 1])
    y_proba = np.array([0.1, 0.2, 0.3, 0.7, 0.8, 0.9, 0.2, 0.85])

    cal = compute_calibration_details(y_true, y_proba, n_bins=4)
    assert "brier_score" in cal
    assert 0.0 <= cal["brier_score"] <= 1.0
    assert "expected_calibration_error" in cal
    assert 0.0 <= cal["expected_calibration_error"] <= 1.0
    assert "calibration_curve" in cal
    assert len(cal["calibration_curve"]["fraction_of_positives"]) > 0


def test_evaluation_pipeline_and_artifact_export():
    """Validates full evaluation pipeline artifact export on dummy data."""
    rng = np.random.RandomState(42)
    n = 60
    data = {col: rng.normal(10.0, 2.0, n) for col in FEATURE_COLUMNS}
    data["dropout_within_horizon"] = rng.choice([0, 1], size=n, p=[0.75, 0.25])
    df = pd.DataFrame(data)

    with tempfile.TemporaryDirectory() as tmp_dir:
        csv_data_path = os.path.join(tmp_dir, "features.csv")
        df.to_csv(csv_data_path, index=False)

        models_dir = os.path.join(tmp_dir, "models")
        eval_dir = os.path.join(tmp_dir, "evaluation")

        lr_dir = os.path.join(models_dir, "logistic_regression")
        xgb_dir = os.path.join(models_dir, "xgboost")

        lr = LogisticRegressionDropoutModel(random_state=42).fit(df[FEATURE_COLUMNS], df["dropout_within_horizon"])
        lr.save(lr_dir)

        xgb = XGBoostDropoutModel(n_estimators=10, random_state=42).fit(df[FEATURE_COLUMNS], df["dropout_within_horizon"])
        xgb.save(xgb_dir)

        res = evaluate_models_and_save_artifacts(
            data_path=csv_data_path,
            eval_dir=eval_dir,
            models_dir=models_dir,
            test_size=0.25,
            random_state=42,
        )

        assert os.path.exists(res["evaluation_summary_json"])
        assert os.path.exists(res["model_comparison_csv"])
        assert os.path.exists(res["calibration_metrics_json"])

        with open(res["evaluation_summary_json"], "r", encoding="utf-8") as f:
            summary = json.load(f)

        assert "models" in summary
        assert "logistic_regression" in summary["models"]
        assert "xgboost" in summary["models"]
        assert summary["models"]["logistic_regression"]["auroc"] >= 0.0
        assert summary["models"]["xgboost"]["auroc"] >= 0.0

        comp_df = pd.read_csv(res["model_comparison_csv"])
        assert len(comp_df) == 2
        assert set(comp_df["model"]).issubset({"logistic_regression", "xgboost"})
