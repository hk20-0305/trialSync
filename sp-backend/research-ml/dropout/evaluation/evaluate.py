"""Comprehensive Model Evaluation & Benchmarking for TrialSync Dropout Prediction (Phase R4).

Evaluates both Logistic Regression and XGBoost on the held-out test partition:
- Discrimination: AUROC, AUPRC
- Classification: Precision, Recall, F1, Specificity, Confusion Matrix
- Calibration: Brier Score, Expected Calibration Error (ECE), Calibration Curves
- Exports: evaluation_summary.json, model_comparison.csv, calibration_metrics.json
"""

from __future__ import annotations

import json
import os
import sys
from typing import Any, Dict, List, Optional, Tuple
import numpy as np
import pandas as pd
from sklearn.calibration import calibration_curve
from sklearn.metrics import brier_score_loss
from sklearn.model_selection import train_test_split

# Ensure research-ml root is on sys.path
CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
RESEARCH_ML_DIR = os.path.abspath(os.path.join(CURRENT_DIR, "..", ".."))
if RESEARCH_ML_DIR not in sys.path:
    sys.path.insert(0, RESEARCH_ML_DIR)

from dropout.evaluation.metrics import compute_binary_classification_metrics
from dropout.models.logistic_regression.model import (
    FEATURE_COLUMNS,
    LogisticRegressionDropoutModel,
)
from dropout.models.xgboost.model import XGBoostDropoutModel


def compute_calibration_details(
    y_true: np.ndarray, y_proba: np.ndarray, n_bins: int = 5
) -> Dict[str, Any]:
    """Computes Brier score, Expected Calibration Error (ECE), and calibration curve points."""
    brier = float(brier_score_loss(y_true, y_proba))

    prob_true, prob_pred = calibration_curve(y_true, y_proba, n_bins=n_bins, strategy="uniform")

    # Estimate Expected Calibration Error (ECE)
    bin_edges = np.linspace(0, 1, n_bins + 1)
    bin_assignments = np.digitize(y_proba, bin_edges) - 1
    bin_assignments = np.clip(bin_assignments, 0, n_bins - 1)

    ece = 0.0
    total_samples = len(y_true)
    for b in range(n_bins):
        mask = bin_assignments == b
        bin_count = np.sum(mask)
        if bin_count > 0:
            bin_acc = np.mean(y_true[mask])
            bin_conf = np.mean(y_proba[mask])
            ece += (bin_count / total_samples) * abs(bin_acc - bin_conf)

    return {
        "brier_score": round(brier, 4),
        "expected_calibration_error": round(float(ece), 4),
        "calibration_curve": {
            "fraction_of_positives": [round(float(p), 4) for p in prob_true],
            "mean_predicted_probability": [round(float(p), 4) for p in prob_pred],
        },
    }


def evaluate_models_and_save_artifacts(
    data_path: Optional[str] = None,
    eval_dir: Optional[str] = None,
    models_dir: Optional[str] = None,
    test_size: float = 0.20,
    random_state: int = 42,
) -> Dict[str, Any]:
    """Runs complete comparative evaluation on held-out test partition and exports artifacts."""
    if data_path is None:
        data_path = os.path.abspath(
            os.path.join(RESEARCH_ML_DIR, "dropout", "data", "features_horizon_90_cutoff_30.csv")
        )
    if eval_dir is None:
        eval_dir = os.path.dirname(os.path.abspath(__file__))
    if models_dir is None:
        models_dir = os.path.abspath(os.path.join(RESEARCH_ML_DIR, "dropout", "models"))

    os.makedirs(eval_dir, exist_ok=True)

    df = pd.read_csv(data_path)
    X = df[FEATURE_COLUMNS].copy()
    y = df["dropout_within_horizon"].astype(int).values

    # Exact stratified split matching training
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=test_size, random_state=random_state, stratify=y
    )

    lr_model = LogisticRegressionDropoutModel.load(os.path.join(models_dir, "logistic_regression"))
    xgb_model = XGBoostDropoutModel.load(os.path.join(models_dir, "xgboost"))

    models = {
        "logistic_regression": lr_model,
        "xgboost": xgb_model,
    }

    comparison_rows = []
    summary: Dict[str, Any] = {
        "evaluation_version": "0.1.0-research",
        "protocol": {
            "observation_cutoff_days": 30,
            "prediction_horizon_days": 90,
            "total_samples": len(df),
            "train_samples": len(X_train),
            "test_samples": len(X_test),
            "train_positives": int(np.sum(y_train == 1)),
            "test_positives": int(np.sum(y_test == 1)),
            "random_seed": random_state,
            "feature_count": len(FEATURE_COLUMNS),
            "features": FEATURE_COLUMNS,
        },
        "models": {},
    }

    calibration_export: Dict[str, Any] = {}

    for name, model in models.items():
        proba = model.predict_proba(X_test)
        base_metrics = compute_binary_classification_metrics(y_test, proba, threshold=0.5)
        cal_metrics = compute_calibration_details(y_test, proba, n_bins=5)

        full_metrics = {**base_metrics, **cal_metrics}
        summary["models"][name] = full_metrics
        calibration_export[name] = cal_metrics

        comparison_rows.append(
            {
                "model": name,
                "auroc": full_metrics["auroc"],
                "auprc": full_metrics["auprc"],
                "precision": full_metrics["precision"],
                "recall": full_metrics["recall"],
                "f1": full_metrics["f1"],
                "specificity": full_metrics["specificity"],
                "brier_score": full_metrics["brier_score"],
                "ece": full_metrics["expected_calibration_error"],
                "true_positives": full_metrics["confusion_matrix"]["true_positives"],
                "false_positives": full_metrics["confusion_matrix"]["false_positives"],
                "true_negatives": full_metrics["confusion_matrix"]["true_negatives"],
                "false_negatives": full_metrics["confusion_matrix"]["false_negatives"],
            }
        )

    # 1. Save evaluation_summary.json
    summary_path = os.path.join(eval_dir, "evaluation_summary.json")
    with open(summary_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2)

    # 2. Save model_comparison.csv
    comparison_df = pd.DataFrame(comparison_rows)
    comparison_csv_path = os.path.join(eval_dir, "model_comparison.csv")
    comparison_df.to_csv(comparison_csv_path, index=False)

    # 3. Save calibration_metrics.json
    calibration_json_path = os.path.join(eval_dir, "calibration_metrics.json")
    with open(calibration_json_path, "w", encoding="utf-8") as f:
        json.dump(calibration_export, f, indent=2)

    # 4. Synchronize comprehensive metadata into model directories
    for name in ["logistic_regression", "xgboost"]:
        meta_file = os.path.join(models_dir, name, "metadata.json")
        if os.path.exists(meta_file):
            with open(meta_file, "r", encoding="utf-8") as mf:
                curr_meta = json.load(mf)
            curr_meta["model_version"] = "0.1.0-research"
            curr_meta["protocol"] = summary["protocol"]
            curr_meta["evaluation_metrics"] = summary["models"][name]
            with open(meta_file, "w", encoding="utf-8") as mf:
                json.dump(curr_meta, mf, indent=2)

    return {
        "evaluation_summary_json": summary_path,
        "model_comparison_csv": comparison_csv_path,
        "calibration_metrics_json": calibration_json_path,
        "summary": summary,
    }


if __name__ == "__main__":
    res = evaluate_models_and_save_artifacts()
    print("Successfully generated evaluation artifacts:")
    for k, v in res.items():
        if k != "summary":
            print(f"  {k}: {v}")
