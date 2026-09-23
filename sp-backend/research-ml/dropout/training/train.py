"""Training and Evaluation orchestration for Dropout ML Models (Phase R4).

Trains and compares:
1. Logistic Regression (Linear baseline with StandardScaler and balanced class weights)
2. XGBoost (Nonlinear gradient-boosted trees with scale_pos_weight)

Enforces strict train/test separation (stratified 80/20 split, seed=42) with zero data leakage.
"""

from __future__ import annotations

import json
import os
import sys
from typing import Any, Dict, Tuple
import numpy as np
import pandas as pd
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


def load_dataset(data_path: str) -> Tuple[pd.DataFrame, pd.Series]:
    """Loads the fixed-horizon dataset and splits into feature matrix X and target y."""
    if not os.path.exists(data_path):
        raise FileNotFoundError(f"Feature dataset not found at: {data_path}")

    df = pd.read_csv(data_path)
    if "dropout_within_horizon" not in df.columns:
        raise KeyError("Dataset missing 'dropout_within_horizon' target column.")

    missing_features = [f for f in FEATURE_COLUMNS if f not in df.columns]
    if missing_features:
        raise KeyError(f"Dataset missing required feature columns: {missing_features}")

    X = df[FEATURE_COLUMNS].copy()
    y = df["dropout_within_horizon"].astype(int).copy()
    return X, y


def train_and_evaluate_all(
    data_path: Optional[str] = None,
    output_base_dir: Optional[str] = None,
    test_size: float = 0.20,
    random_state: int = 42,
) -> Dict[str, Any]:
    """Orchestrates training, held-out evaluation, and serialization for both models."""
    if data_path is None:
        data_path = os.path.abspath(
            os.path.join(RESEARCH_ML_DIR, "dropout", "data", "features_horizon_90_cutoff_30.csv")
        )

    if output_base_dir is None:
        output_base_dir = os.path.abspath(os.path.join(RESEARCH_ML_DIR, "dropout", "models"))

    lr_dir = os.path.join(output_base_dir, "logistic_regression")
    xgb_dir = os.path.join(output_base_dir, "xgboost")

    print(f"[Phase R4] Loading dataset: {data_path}")
    X, y = load_dataset(data_path)
    print(f"[Phase R4] Total samples: {len(X)} with {len(FEATURE_COLUMNS)} features.")
    print(f"[Phase R4] Overall positive prevalence: {y.mean():.2%} ({y.sum()}/{len(y)})")

    # Stratified 80/20 train/test split
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=test_size, random_state=random_state, stratify=y
    )
    print(
        f"[Phase R4] Train set: {len(X_train)} samples ({y_train.sum()} dropouts, {len(y_train) - y_train.sum()} retained)"
    )
    print(
        f"[Phase R4] Test set:  {len(X_test)} samples ({y_test.sum()} dropouts, {len(y_test) - y_test.sum()} retained)"
    )

    results: Dict[str, Any] = {
        "train_samples": len(X_train),
        "test_samples": len(X_test),
        "features_count": len(FEATURE_COLUMNS),
        "models": {},
    }

    # 1. Train & Evaluate Logistic Regression
    print("\n" + "=" * 55)
    print("Training Model 1: Logistic Regression (Interpretable Baseline)")
    print("=" * 55)
    lr_model = LogisticRegressionDropoutModel(c_param=1.0, max_iter=1000, random_state=random_state)
    lr_model.fit(X_train, y_train)

    lr_test_proba = lr_model.predict_proba(X_test)
    lr_metrics = compute_binary_classification_metrics(y_test, lr_test_proba, threshold=0.5)
    lr_saved = lr_model.save(lr_dir, metrics=lr_metrics)

    results["models"]["logistic_regression"] = {
        "metrics": lr_metrics,
        "saved_artifacts": lr_saved,
    }
    print(f"  AUROC:     {lr_metrics['auroc']:.4f}")
    print(f"  AUPRC:     {lr_metrics['auprc']:.4f}")
    print(f"  Precision: {lr_metrics['precision']:.4f}")
    print(f"  Recall:    {lr_metrics['recall']:.4f}")
    print(f"  F1:        {lr_metrics['f1']:.4f}")
    print(f"  Confusion Matrix: {lr_metrics['confusion_matrix']['matrix']}")

    # 2. Train & Evaluate XGBoost
    print("\n" + "=" * 55)
    print("Training Model 2: XGBoost (Gradient Boosted Trees)")
    print("=" * 55)
    xgb_model = XGBoostDropoutModel(
        n_estimators=100,
        max_depth=4,
        learning_rate=0.05,
        subsample=0.8,
        colsample_bytree=0.8,
        random_state=random_state,
    )
    xgb_model.fit(X_train, y_train)

    xgb_test_proba = xgb_model.predict_proba(X_test)
    xgb_metrics = compute_binary_classification_metrics(y_test, xgb_test_proba, threshold=0.5)
    xgb_saved = xgb_model.save(xgb_dir, metrics=xgb_metrics)

    results["models"]["xgboost"] = {
        "metrics": xgb_metrics,
        "saved_artifacts": xgb_saved,
    }
    print(f"  AUROC:     {xgb_metrics['auroc']:.4f}")
    print(f"  AUPRC:     {xgb_metrics['auprc']:.4f}")
    print(f"  Precision: {xgb_metrics['precision']:.4f}")
    print(f"  Recall:    {xgb_metrics['recall']:.4f}")
    print(f"  F1:        {xgb_metrics['f1']:.4f}")
    print(f"  Confusion Matrix: {xgb_metrics['confusion_matrix']['matrix']}")

    # Top feature importances in XGBoost
    top_5 = list(xgb_model.training_metadata.get("feature_importances", {}).items())[:5]
    print(f"  Top 5 Features: {top_5}")

    print("\n" + "=" * 55)
    print("Model Training & Evaluation Completed Successfully")
    print("=" * 55)

    return results


if __name__ == "__main__":
    train_and_evaluate_all()
