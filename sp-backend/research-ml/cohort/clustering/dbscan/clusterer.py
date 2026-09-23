"""DBSCAN Clustering for TrialSync Cohort Atlas (Phase R6).

Performs density-based unsupervised clustering on normalized clinical feature vectors
to discover sub-cohort phenotypes and identify clinical outlier/noise points.
"""

from __future__ import annotations

import json
import os
from typing import Any, Dict, List, Optional, Tuple, Union
import joblib
import numpy as np
import pandas as pd
from sklearn.cluster import DBSCAN


class CohortDBSCANClusterer:
    """Unsupervised density-based clustering identifying clinical phenotypes and outlier noise."""

    def __init__(self, eps: float = 0.6, min_samples: int = 10):
        self.eps = eps
        self.min_samples = min_samples
        self.model = DBSCAN(eps=self.eps, min_samples=self.min_samples, metric="euclidean")
        self.labels_: Optional[np.ndarray] = None
        self.metadata: Dict[str, Any] = {}

    def fit_predict(
        self,
        participant_ids: List[str],
        X_scaled: np.ndarray,
        feature_names: Optional[List[str]] = None,
        X_raw: Optional[np.ndarray] = None,
    ) -> np.ndarray:
        """Executes DBSCAN clustering on standardized features and computes cluster statistics."""
        self.labels_ = self.model.fit_predict(X_scaled)

        unique_labels = set(self.labels_)
        n_clusters = len(unique_labels - {-1})
        n_noise = int(np.sum(self.labels_ == -1))

        cluster_counts = {int(lbl): int(np.sum(self.labels_ == lbl)) for lbl in unique_labels}

        # Compute cluster profiles across features if raw or scaled values provided
        profiles: Dict[str, Dict[str, float]] = {}
        matrix_for_profiling = X_raw if X_raw is not None else X_scaled
        names = feature_names if feature_names is not None else [f"f_{i}" for i in range(X_scaled.shape[1])]

        for lbl in sorted(unique_labels):
            lbl_name = "noise" if lbl == -1 else f"cluster_{lbl}"
            mask = self.labels_ == lbl
            subset = matrix_for_profiling[mask]
            mean_vals = np.mean(subset, axis=0)
            profiles[lbl_name] = {
                "size": int(np.sum(mask)),
                "size_pct": round(float(np.sum(mask) / len(self.labels_)), 4),
                "means": {feat: round(float(m), 4) for feat, m in zip(names, mean_vals)},
            }

        self.metadata = {
            "algorithm": "DBSCAN",
            "hyperparameters": {
                "eps": self.eps,
                "min_samples": self.min_samples,
                "metric": "euclidean",
            },
            "n_samples": len(participant_ids),
            "n_clusters": n_clusters,
            "n_noise_points": n_noise,
            "noise_percentage": round(float(n_noise / len(self.labels_)), 4),
            "cluster_sizes": cluster_counts,
            "cluster_profiles": profiles,
        }

        return self.labels_

    def save_artifacts(
        self,
        output_dir: str,
        participant_ids: List[str],
    ) -> Dict[str, str]:
        """Saves cluster assignment table, cluster metadata, and trained model artifact."""
        if self.labels_ is None:
            raise RuntimeError("Model must be fitted before saving artifacts.")

        os.makedirs(output_dir, exist_ok=True)

        # 1. Clusters CSV
        clusters_csv_path = os.path.join(output_dir, "clusters.csv")
        clusters_df = pd.DataFrame(
            {
                "participant_id": participant_ids,
                "cluster_label": [int(lbl) for lbl in self.labels_],
                "is_noise": [bool(lbl == -1) for lbl in self.labels_],
            }
        )
        clusters_df.to_csv(clusters_csv_path, index=False)

        # 2. Metadata JSON
        meta_path = os.path.join(output_dir, "cluster_metadata.json")
        with open(meta_path, "w", encoding="utf-8") as f:
            json.dump(self.metadata, f, indent=2)

        # 3. Model joblib
        model_path = os.path.join(output_dir, "model.joblib")
        joblib.dump(self.model, model_path)

        return {
            "clusters_csv": clusters_csv_path,
            "metadata_json": meta_path,
            "model_path": model_path,
        }
