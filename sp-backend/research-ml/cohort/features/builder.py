"""Cohort Feature Builder for TrialSync Cohort Atlas (Phase R6).

Extracts and standardizes a deterministic 33-feature numerical vector for every
research participant, strictly isolated from target/outcome leakage (no dropout endpoints).
"""

from __future__ import annotations

import json
import os
from typing import Any, Dict, List, Optional, Tuple, Union
import joblib
import numpy as np
import pandas as pd
from sklearn.preprocessing import StandardScaler


COHORT_FEATURE_COLUMNS = [
    # Demographics & Baseline (6)
    "age",
    "is_female",
    "is_experimental_arm",
    "baseline_health_score",
    "is_high_travel_burden",
    "frailty_index",
    # Dosing Adherence (6)
    "doses_scheduled_pre_cutoff",
    "doses_administered_pre_cutoff",
    "doses_missed_pre_cutoff",
    "doses_reduced_pre_cutoff",
    "adherence_ratio_pre_cutoff",
    "cumulative_dose_mg_pre_cutoff",
    # Visit Attendance (4)
    "visits_scheduled_pre_cutoff",
    "visits_attended_pre_cutoff",
    "visits_missed_pre_cutoff",
    "visit_attendance_rate_pre_cutoff",
    # Adverse Events & Burden (5)
    "ae_count_pre_cutoff",
    "ae_max_grade_pre_cutoff",
    "ae_serious_count_pre_cutoff",
    "ae_burden_score_pre_cutoff",
    "has_drug_related_ae_pre_cutoff",
    # Measurements & Trends (12)
    "measurement_count_pre_cutoff",
    "abnormal_measurement_count_pre_cutoff",
    "abnormal_measurement_rate_pre_cutoff",
    "systolic_bp_baseline",
    "systolic_bp_latest_pre_cutoff",
    "systolic_bp_change_pre_cutoff",
    "platelets_baseline",
    "platelets_latest_pre_cutoff",
    "platelets_pct_change_pre_cutoff",
    "alt_baseline",
    "alt_latest_pre_cutoff",
    "alt_elevation_ratio_pre_cutoff",
]


class CohortFeatureBuilder:
    """Extracts, validates, and normalizes tabular clinical features for cohort discovery."""

    def __init__(self):
        self.feature_names = list(COHORT_FEATURE_COLUMNS)
        self.scaler = StandardScaler()
        self.is_fitted = False
        self.metadata: Dict[str, Any] = {}

    def extract_features_and_ids(
        self, df: pd.DataFrame
    ) -> Tuple[List[str], np.ndarray]:
        """Validates schema, checks for leakage, and extracts participant IDs and raw feature array."""
        # 1. Leakage guard
        forbidden_leakage = ["dropout_within_horizon", "dropout_day", "dropout_reason"]
        # We ensure none of the forbidden columns are in self.feature_names
        for col in forbidden_leakage:
            assert col not in self.feature_names, f"CRITICAL: Leakage column {col} in feature names"

        # 2. Extract participant IDs
        if "participant_id" in df.columns:
            participant_ids = df["participant_id"].astype(str).tolist()
        elif "id" in df.columns:
            participant_ids = df["id"].astype(str).tolist()
        else:
            participant_ids = [f"PARTICIPANT-{i:04d}" for i in range(len(df))]

        # 3. Check for missing columns
        missing = [f for f in self.feature_names if f not in df.columns]
        if missing:
            raise ValueError(f"Missing required cohort feature columns: {missing}")

        # 4. Extract raw matrix and fill NaNs explicitly if any
        feature_df = df[self.feature_names].copy().fillna(0.0)
        X_raw = feature_df.values.astype(np.float32)

        return participant_ids, X_raw

    def fit_transform(
        self, df: pd.DataFrame
    ) -> Tuple[List[str], np.ndarray, np.ndarray]:
        """Extracts features, fits StandardScaler, and returns (ids, raw_matrix, scaled_matrix)."""
        ids, X_raw = self.extract_features_and_ids(df)
        X_scaled = self.scaler.fit_transform(X_raw).astype(np.float32)
        self.is_fitted = True

        self.metadata = {
            "n_samples": len(ids),
            "n_features": len(self.feature_names),
            "feature_names": self.feature_names,
            "scaler_means": {
                feat: float(m) for feat, m in zip(self.feature_names, self.scaler.mean_)
            },
            "scaler_scales": {
                feat: float(s) for feat, s in zip(self.feature_names, self.scaler.scale_)
            },
        }
        return ids, X_raw, X_scaled

    def transform(self, df: pd.DataFrame) -> Tuple[List[str], np.ndarray]:
        """Transforms new features using previously fitted StandardScaler."""
        if not self.is_fitted:
            raise RuntimeError("CohortFeatureBuilder must be fitted before transform.")
        ids, X_raw = self.extract_features_and_ids(df)
        X_scaled = self.scaler.transform(X_raw).astype(np.float32)
        return ids, X_scaled

    def save_artifacts(
        self,
        output_dir: str,
        participant_ids: List[str],
        X_scaled: np.ndarray,
    ) -> Dict[str, str]:
        """Saves scaled feature matrix, metadata, and fitted scaler artifact to disk."""
        os.makedirs(output_dir, exist_ok=True)

        # 1. Feature matrix CSV
        features_csv_path = os.path.join(output_dir, "cohort_features.csv")
        export_df = pd.DataFrame(X_scaled, columns=self.feature_names)
        export_df.insert(0, "participant_id", participant_ids)
        export_df.to_csv(features_csv_path, index=False)

        # 2. Scaler joblib
        scaler_path = os.path.join(output_dir, "scaler.joblib")
        joblib.dump(self.scaler, scaler_path)

        # 3. Metadata JSON
        meta_path = os.path.join(output_dir, "feature_metadata.json")
        with open(meta_path, "w", encoding="utf-8") as f:
            json.dump(self.metadata, f, indent=2)

        return {
            "features_csv": features_csv_path,
            "scaler_path": scaler_path,
            "metadata_path": meta_path,
        }
