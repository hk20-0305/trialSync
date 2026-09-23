"""PCA Dimensionality Reduction for TrialSync Cohort Atlas (Phase R6).

Projects the high-dimensional clinical feature matrix onto a 2-dimensional latent space (PC1, PC2)
for cohort visualization, preserving participant IDs and cluster memberships.
"""

from __future__ import annotations

import json
import os
from typing import Any, Dict, List, Optional, Tuple, Union
import joblib
import numpy as np
import pandas as pd
from sklearn.decomposition import PCA


class CohortPCAReducer:
    """Performs 2D principal component analysis for cohort visualization."""

    def __init__(self, n_components: int = 2, random_state: int = 42):
        self.n_components = n_components
        self.random_state = random_state
        self.pca = PCA(n_components=self.n_components, random_state=self.random_state)
        self.is_fitted = False
        self.metadata: Dict[str, Any] = {}

    def fit_transform(
        self,
        X_scaled: np.ndarray,
        participant_ids: List[str],
        cluster_labels: Optional[List[int]] = None,
        feature_names: Optional[List[str]] = None,
    ) -> pd.DataFrame:
        """Fits PCA and projects the cohort onto PC1 and PC2."""
        coords_2d = self.pca.fit_transform(X_scaled)
        self.is_fitted = True

        exp_var = [round(float(v), 5) for v in self.pca.explained_variance_ratio_]
        total_var = round(float(np.sum(exp_var)), 5)

        # Record top loadings per component if feature names provided
        loadings: Dict[str, Dict[str, float]] = {}
        if feature_names is not None:
            for i in range(self.n_components):
                comp_loadings = {
                    feat: round(float(coef), 4)
                    for feat, coef in zip(feature_names, self.pca.components_[i])
                }
                loadings[f"PC{i + 1}"] = comp_loadings

        self.metadata = {
            "n_components": self.n_components,
            "random_state": self.random_state,
            "explained_variance_ratio": {
                f"PC{i + 1}": exp_var[i] for i in range(self.n_components)
            },
            "cumulative_explained_variance": total_var,
            "singular_values": [round(float(s), 4) for s in self.pca.singular_values_],
            "loadings": loadings,
        }

        # Build output dataframe
        df_proj = pd.DataFrame(
            {
                "participant_id": participant_ids,
                "pc1": [round(float(c[0]), 5) for c in coords_2d],
                "pc2": [round(float(c[1]), 5) for c in coords_2d],
            }
        )

        if cluster_labels is not None:
            df_proj["cluster_label"] = [int(lbl) for lbl in cluster_labels]
            df_proj["is_noise"] = [bool(lbl == -1) for lbl in cluster_labels]

        return df_proj

    def save_artifacts(
        self,
        output_dir: str,
        projection_df: pd.DataFrame,
    ) -> Dict[str, str]:
        """Saves 2D projection coordinates, explained variance metadata, and trained PCA model."""
        if not self.is_fitted:
            raise RuntimeError("PCA must be fitted before saving artifacts.")

        os.makedirs(output_dir, exist_ok=True)

        # 1. Projection CSV
        proj_csv_path = os.path.join(output_dir, "pca_projection.csv")
        projection_df.to_csv(proj_csv_path, index=False)

        # 2. Metadata JSON
        meta_path = os.path.join(output_dir, "pca_metadata.json")
        with open(meta_path, "w", encoding="utf-8") as f:
            json.dump(self.metadata, f, indent=2)

        # 3. Model joblib
        model_path = os.path.join(output_dir, "pca_model.joblib")
        joblib.dump(self.pca, model_path)

        return {
            "projection_csv": proj_csv_path,
            "metadata_json": meta_path,
            "model_path": model_path,
        }
