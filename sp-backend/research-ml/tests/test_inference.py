"""Unit tests for DropoutRiskPredictor inference pipeline (Phase R4)."""

from __future__ import annotations

import os
import tempfile
import numpy as np
import pandas as pd
import pytest

from dropout.inference.predictor import DropoutRiskPredictor
from dropout.models.logistic_regression.model import (
    FEATURE_COLUMNS,
    LogisticRegressionDropoutModel,
)
from dropout.models.xgboost.model import XGBoostDropoutModel


@pytest.fixture
def trained_models():
    """Trains dummy LR and XGB models and saves them to temporary directories."""
    rng = np.random.RandomState(42)
    n = 100
    data = {col: rng.normal(10.0, 2.0, n) for col in FEATURE_COLUMNS}
    df = pd.DataFrame(data)
    y = rng.choice([0, 1], size=n, p=[0.75, 0.25])

    tmp_dir = tempfile.mkdtemp()
    lr_dir = os.path.join(tmp_dir, "lr")
    xgb_dir = os.path.join(tmp_dir, "xgb")

    lr = LogisticRegressionDropoutModel(random_state=42).fit(df, y)
    lr.save(lr_dir)

    xgb_mod = XGBoostDropoutModel(n_estimators=10, random_state=42).fit(df, y)
    xgb_mod.save(xgb_dir)

    return {"lr_dir": lr_dir, "xgb_dir": xgb_dir, "sample_df": df}


def test_predictor_single_record(trained_models):
    """Validates single-record inference and risk tier assignment for both models."""
    sample_record = trained_models["sample_df"].iloc[0].to_dict()

    for mtype, mdir in [("logistic_regression", trained_models["lr_dir"]), ("xgboost", trained_models["xgb_dir"])]:
        predictor = DropoutRiskPredictor(model_type=mtype, model_dir=mdir, threshold=0.5)
        res = predictor.predict_record(sample_record)

        assert "dropout_probability" in res
        assert 0.0 <= res["dropout_probability"] <= 1.0
        assert res["risk_tier"] in ("LOW", "MEDIUM", "HIGH")
        assert isinstance(res["predicted_dropout"], bool)


def test_predictor_batch(trained_models):
    """Validates batch DataFrame inference and column appending."""
    df = trained_models["sample_df"]
    predictor = DropoutRiskPredictor(
        model_type="xgboost", model_dir=trained_models["xgb_dir"], threshold=0.5
    )

    batch_res = predictor.predict_batch(df)
    assert len(batch_res) == len(df)
    assert "predicted_dropout_probability" in batch_res.columns
    assert "predicted_dropout" in batch_res.columns
    assert "dropout_risk_tier" in batch_res.columns

    # Verify probability bounds
    assert (batch_res["predicted_dropout_probability"] >= 0.0).all()
    assert (batch_res["predicted_dropout_probability"] <= 1.0).all()


def test_predictor_unsupported_model_type():
    """Validates that unknown model types raise a ValueError."""
    with pytest.raises(ValueError, match="Unsupported model_type"):
        DropoutRiskPredictor(model_type="random_forest", model_dir=".")
