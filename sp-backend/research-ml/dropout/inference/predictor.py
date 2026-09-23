"""Inference module for clinical dropout risk prediction (Phase R4).

Loads trained Logistic Regression or XGBoost models and produces clinical risk scores,
risk tiers (Low, Medium, High), and inference payloads.
"""

from __future__ import annotations

import os
from typing import Any, Dict, List, Optional, Union
import numpy as np
import pandas as pd

from dropout.models.logistic_regression.model import LogisticRegressionDropoutModel
from dropout.models.xgboost.model import XGBoostDropoutModel


class DropoutRiskPredictor:
    """Unified inference predictor for clinical dropout risk."""

    def __init__(
        self,
        model_type: str = "xgboost",
        model_dir: Optional[str] = None,
        threshold: float = 0.5,
    ):
        self.model_type = model_type.lower()
        self.threshold = threshold

        if model_dir is None:
            base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "models"))
            if self.model_type in ("logistic_regression", "lr"):
                model_dir = os.path.join(base_dir, "logistic_regression")
            else:
                model_dir = os.path.join(base_dir, "xgboost")

        self.model_dir = model_dir
        self.model: Union[LogisticRegressionDropoutModel, XGBoostDropoutModel]

        if self.model_type in ("logistic_regression", "lr"):
            self.model = LogisticRegressionDropoutModel.load(self.model_dir)
        elif self.model_type in ("xgboost", "xgb"):
            self.model = XGBoostDropoutModel.load(self.model_dir)
        else:
            raise ValueError(f"Unsupported model_type: {model_type}. Expected 'logistic_regression' or 'xgboost'.")

        self.feature_names = self.model.feature_names

    @staticmethod
    def classify_risk_tier(probability: float) -> str:
        """Assigns clinically actionable risk tiers based on dropout probability."""
        if probability < 0.30:
            return "LOW"
        elif probability < 0.60:
            return "MEDIUM"
        return "HIGH"

    def predict_record(self, record: Dict[str, Any]) -> Dict[str, Any]:
        """Performs single-participant dropout risk inference."""
        df = pd.DataFrame([record])
        proba = float(self.model.predict_proba(df)[0])
        tier = self.classify_risk_tier(proba)
        pred = bool(proba >= self.threshold)

        return {
            "dropout_probability": round(proba, 4),
            "predicted_dropout": pred,
            "risk_tier": tier,
            "threshold": self.threshold,
            "model_type": self.model_type,
        }

    def predict_batch(self, df: pd.DataFrame) -> pd.DataFrame:
        """Performs batch dropout risk inference on a dataframe of participant features."""
        probas = self.model.predict_proba(df)
        preds = (probas >= self.threshold).astype(int)
        tiers = [self.classify_risk_tier(p) for p in probas]

        results_df = df.copy()
        results_df["predicted_dropout_probability"] = [round(float(p), 4) for p in probas]
        results_df["predicted_dropout"] = preds
        results_df["dropout_risk_tier"] = tiers
        return results_df
