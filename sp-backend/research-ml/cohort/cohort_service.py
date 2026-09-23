"""In-memory service and store for Cohort Atlas artifacts (Phase R9).

Loads and caches R6 artifacts (DBSCAN clusters, PCA projections, FAISS similarity index)
to serve low-latency HTTP queries without regenerating data or re-reading disk per request.
"""

from __future__ import annotations

import json
import os
from typing import Any, Dict, List, Optional
import pandas as pd

from cohort.similarity.faiss.indexer import CohortFaissIndexer


class CohortArtifactStore:
    """Thread-safe, read-only in-memory cache for R6 Cohort Atlas artifacts."""

    def __init__(self, base_dir: Optional[str] = None):
        if base_dir is None:
            # Defaults to sp-backend/research-ml/cohort
            base_dir = os.path.dirname(os.path.abspath(__file__))
        self.base_dir = base_dir

        self.is_loaded = False
        self.indexer: Optional[CohortFaissIndexer] = None
        self.projection_records: List[Dict[str, Any]] = []
        self.participant_lookup: Dict[str, Dict[str, Any]] = {}
        self.cluster_metadata: Dict[str, Any] = {}
        self.pca_metadata: Dict[str, Any] = {}
        self.index_metadata: Dict[str, Any] = {}
        self.feature_metadata: Dict[str, Any] = {}
        self.cluster_summaries: List[Dict[str, Any]] = []

    def load_artifacts(self) -> None:
        """Loads all R6 serialized artifacts into memory."""
        faiss_dir = os.path.join(self.base_dir, "similarity", "faiss")
        pca_dir = os.path.join(self.base_dir, "dimensionality_reduction", "pca")
        dbscan_dir = os.path.join(self.base_dir, "clustering", "dbscan")
        feature_dir = os.path.join(self.base_dir, "features")

        # 1. Load FAISS Index and mappings
        self.indexer = CohortFaissIndexer.load(faiss_dir)

        # 2. Load PCA projection
        proj_csv_path = os.path.join(pca_dir, "pca_projection.csv")
        if not os.path.exists(proj_csv_path):
            raise FileNotFoundError(f"Missing PCA projection at {proj_csv_path}")
        df_proj = pd.read_csv(proj_csv_path)

        self.projection_records = []
        self.participant_lookup = {}
        for _, row in df_proj.iterrows():
            item = {
                "participant_id": str(row["participant_id"]),
                "pc1": round(float(row["pc1"]), 5),
                "pc2": round(float(row["pc2"]), 5),
                "cluster_label": int(row["cluster_label"]),
                "is_noise": bool(row["is_noise"]),
            }
            self.projection_records.append(item)
            self.participant_lookup[item["participant_id"]] = item

        # 3. Load Metadata JSONs
        with open(os.path.join(pca_dir, "pca_metadata.json"), "r", encoding="utf-8") as f:
            self.pca_metadata = json.load(f)

        with open(os.path.join(dbscan_dir, "cluster_metadata.json"), "r", encoding="utf-8") as f:
            self.cluster_metadata = json.load(f)

        with open(os.path.join(faiss_dir, "index_metadata.json"), "r", encoding="utf-8") as f:
            self.index_metadata = json.load(f)

        with open(os.path.join(feature_dir, "feature_metadata.json"), "r", encoding="utf-8") as f:
            self.feature_metadata = json.load(f)

        # 4. Build cluster summaries
        self.cluster_summaries = []
        cluster_sizes = self.cluster_metadata.get("cluster_sizes", {})
        cluster_profiles = self.cluster_metadata.get("cluster_profiles", {})
        total_samples = self.cluster_metadata.get("n_samples", len(self.projection_records))

        for lbl_str, size in sorted(cluster_sizes.items(), key=lambda x: int(x[0])):
            lbl = int(lbl_str)
            profile_key = "noise" if lbl == -1 else f"cluster_{lbl}"
            profile = cluster_profiles.get(profile_key, {})
            pct = round(float(size / total_samples), 4) if total_samples > 0 else 0.0
            self.cluster_summaries.append(
                {
                    "cluster_label": lbl,
                    "size": int(size),
                    "size_pct": pct,
                    "is_noise": (lbl == -1),
                    "means": profile.get("means", {}),
                }
            )

        self.is_loaded = True

    def get_health(self) -> Dict[str, Any]:
        return {
            "status": "UP" if self.is_loaded else "UNINITIALIZED",
            "participant_count": len(self.projection_records),
            "faiss_total_indexed": self.indexer.index.ntotal if self.indexer and self.indexer.index else 0,
            "cluster_count": self.cluster_metadata.get("n_clusters", 0),
        }

    def get_summary(self) -> Dict[str, Any]:
        if not self.is_loaded:
            raise RuntimeError("Cohort artifacts are not loaded.")

        return {
            "participant_count": self.cluster_metadata.get("n_samples", len(self.projection_records)),
            "feature_dimension": self.index_metadata.get("dimension", 33),
            "cluster_count": self.cluster_metadata.get("n_clusters", 0),
            "noise_count": self.cluster_metadata.get("n_noise_points", 0),
            "noise_percentage": self.cluster_metadata.get("noise_percentage", 1.0),
            "dbscan_parameters": self.cluster_metadata.get("hyperparameters", {}),
            "pca_explained_variance": {
                "explained_variance_ratio": self.pca_metadata.get("explained_variance_ratio", {}),
                "cumulative_explained_variance": self.pca_metadata.get("cumulative_explained_variance", 0.0),
            },
            "artifact_metadata": {
                "faiss_metric": self.index_metadata.get("metric", "L2_EUCLIDEAN"),
                "faiss_index_type": self.index_metadata.get("index_type", "IndexFlatL2"),
                "pca_n_components": self.pca_metadata.get("n_components", 2),
                "feature_count": self.feature_metadata.get("n_features", 33),
            },
        }

    def get_projection(
        self,
        page: int = 1,
        page_size: int = 50,
        cluster_label: Optional[int] = None,
    ) -> Dict[str, Any]:
        if not self.is_loaded:
            raise RuntimeError("Cohort artifacts are not loaded.")

        filtered = self.projection_records
        if cluster_label is not None:
            filtered = [r for r in filtered if r["cluster_label"] == cluster_label]

        total_items = len(filtered)
        # 1-indexed pagination
        start_idx = (page - 1) * page_size
        end_idx = start_idx + page_size
        items = filtered[start_idx:end_idx] if start_idx < total_items else []

        total_pages = (total_items + page_size - 1) // page_size if total_items > 0 else 0

        return {
            "total": total_items,
            "page": page,
            "page_size": page_size,
            "total_pages": total_pages,
            "cluster_filter": cluster_label,
            "items": items,
        }

    def get_clusters(self) -> List[Dict[str, Any]]:
        if not self.is_loaded:
            raise RuntimeError("Cohort artifacts are not loaded.")
        return self.cluster_summaries

    def get_nearest_neighbors(
        self, participant_id: str, k: int = 5
    ) -> Dict[str, Any]:
        if not self.is_loaded or self.indexer is None:
            raise RuntimeError("Cohort artifacts are not loaded.")

        if participant_id not in self.indexer.id_to_idx:
            raise KeyError(f"Participant ID '{participant_id}' not present in cohort index.")

        neighbors = self.indexer.query_by_participant_id(
            participant_id=participant_id, k=k, include_self=False
        )

        return {
            "participant_id": participant_id,
            "k": k,
            "neighbors": neighbors,
        }
