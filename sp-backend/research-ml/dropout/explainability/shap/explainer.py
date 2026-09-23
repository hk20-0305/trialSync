"""SHAP explainability module for TrialSync Dropout Prediction (Phase R4).

Utilizes shap.TreeExplainer on the trained XGBoost model to generate:
- Global feature importance (mean absolute SHAP value ranking)
- Local patient-level explanations (feature value, contribution direction, magnitude, base value)
- Artifact export to JSON/CSV for clinical auditability.
"""

from __future__ import annotations

import json
import os
from typing import Any, Dict, List, Optional, Tuple, Union
import joblib
import numpy as np
import pandas as pd
import shap

from dropout.models.xgboost.model import FEATURE_COLUMNS, XGBoostDropoutModel


class ShapDropoutExplainer:
    """Computes and serializes global and local SHAP explanations for XGBoost dropout models."""

    def __init__(self, model: Optional[XGBoostDropoutModel] = None, model_dir: Optional[str] = None):
        if model is not None:
            self.model = model
        elif model_dir is not None:
            self.model = XGBoostDropoutModel.load(model_dir)
        else:
            default_dir = os.path.abspath(
                os.path.join(os.path.dirname(__file__), "..", "..", "models", "xgboost")
            )
            self.model = XGBoostDropoutModel.load(default_dir)

        if not self.model.is_fitted or self.model.classifier is None:
            raise RuntimeError("Underlying XGBoost model must be fitted to compute SHAP values.")

        self.feature_names = self.model.feature_names
        # Create TreeExplainer on underlying XGBoost booster/classifier
        self.explainer = shap.TreeExplainer(self.model.classifier)
        self.expected_value = float(
            self.explainer.expected_value
            if not isinstance(self.explainer.expected_value, (list, np.ndarray))
            else self.explainer.expected_value[0]
        )

    def extract_features(self, X: Union[pd.DataFrame, np.ndarray]) -> np.ndarray:
        """Extracts and orders features as a 2D numpy array."""
        if isinstance(X, pd.DataFrame):
            return self.model.extract_features(X).values
        return np.asarray(X)

    def compute_shap_values(self, X: Union[pd.DataFrame, np.ndarray]) -> np.ndarray:
        """Computes raw SHAP values matrix for input data."""
        X_mat = self.extract_features(X)
        shap_vals = self.explainer.shap_values(X_mat)
        if isinstance(shap_vals, list):
            # For some tree classifier versions, returns list of [neg, pos]
            shap_vals = shap_vals[1]
        return np.asarray(shap_vals)

    def compute_global_importance(self, X: Union[pd.DataFrame, np.ndarray]) -> List[Dict[str, Any]]:
        """Calculates global mean absolute SHAP importance per feature sorted descending."""
        X_mat = self.extract_features(X)
        shap_vals = self.compute_shap_values(X_mat)
        mean_abs = np.mean(np.abs(shap_vals), axis=0)

        importance_list = []
        for feat, score in zip(self.feature_names, mean_abs):
            importance_list.append(
                {
                    "feature": feat,
                    "mean_abs_shap": round(float(score), 5),
                }
            )

        importance_list.sort(key=lambda x: x["mean_abs_shap"], reverse=True)
        return importance_list

    def explain_local_prediction(
        self, record: Union[pd.Series, Dict[str, Any]], top_k: int = 10
    ) -> Dict[str, Any]:
        """Computes local explanation for a single participant.

        Returns feature contributions sorted by absolute magnitude, detailing:
        - feature name
        - observed value
        - shap_value (magnitude and sign)
        - direction ("INCREASES_RISK" if positive, "DECREASES_RISK" if negative)
        """
        if isinstance(record, dict):
            df = pd.DataFrame([record])
        elif isinstance(record, pd.Series):
            df = pd.DataFrame([record.to_dict()])
        else:
            df = pd.DataFrame(record)

        X_mat = self.extract_features(df)
        shap_vals = self.compute_shap_values(X_mat)[0]
        feature_vals = X_mat[0]

        contributions: List[Dict[str, Any]] = []
        for feat, val, s_val in zip(self.feature_names, feature_vals, shap_vals):
            direction = "INCREASES_RISK" if s_val > 0 else ("DECREASES_RISK" if s_val < 0 else "NEUTRAL")
            contributions.append(
                {
                    "feature": feat,
                    "value": round(float(val), 4),
                    "shap_value": round(float(s_val), 5),
                    "abs_magnitude": round(float(abs(s_val)), 5),
                    "direction": direction,
                }
            )

        contributions.sort(key=lambda x: x["abs_magnitude"], reverse=True)

        predicted_prob = float(self.model.predict_proba(df)[0])

        return {
            "base_value": round(self.expected_value, 5),
            "predicted_probability": round(predicted_prob, 4),
            "top_contributions": contributions[:top_k],
            "all_contributions": contributions,
        }

    def save_artifacts(
        self,
        X_background: Union[pd.DataFrame, np.ndarray],
        output_dir: Optional[str] = None,
        sample_records: Optional[List[Dict[str, Any]]] = None,
    ) -> Dict[str, str]:
        """Saves global importance (JSON & CSV) and local explanation artifacts to disk."""
        if output_dir is None:
            output_dir = os.path.dirname(os.path.abspath(__file__))

        os.makedirs(output_dir, exist_ok=True)

        # 1. Global Importance
        global_imp = self.compute_global_importance(X_background)
        json_path = os.path.join(output_dir, "global_importance.json")
        csv_path = os.path.join(output_dir, "global_importance.csv")

        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(
                {
                    "method": "shap.TreeExplainer",
                    "n_background_samples": len(X_background),
                    "base_value": round(self.expected_value, 5),
                    "ranking": global_imp,
                },
                f,
                indent=2,
            )

        pd.DataFrame(global_imp).to_csv(csv_path, index=False)

        # 2. Local Sample Explanations (if provided or top 2 rows from background)
        local_samples_path = os.path.join(output_dir, "sample_local_explanations.json")
        samples_to_explain = sample_records
        if samples_to_explain is None:
            if isinstance(X_background, pd.DataFrame):
                samples_to_explain = X_background.head(3).to_dict(orient="records")
            else:
                samples_to_explain = [
                    dict(zip(self.feature_names, row)) for row in X_background[:3]
                ]

        local_explanations = [
            self.explain_local_prediction(s, top_k=8) for s in samples_to_explain
        ]

        with open(local_samples_path, "w", encoding="utf-8") as f:
            json.dump(local_explanations, f, indent=2)

        # 3. Explainer Joblib
        explainer_path = os.path.join(output_dir, "explainer.joblib")
        joblib.dump(self.explainer, explainer_path)

        return {
            "global_importance_json": json_path,
            "global_importance_csv": csv_path,
            "sample_local_explanations": local_samples_path,
            "explainer_path": explainer_path,
        }
