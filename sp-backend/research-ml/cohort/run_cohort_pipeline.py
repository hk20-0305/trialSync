"""Full execution pipeline for Cohort Atlas (Phase R6).

Orchestrates:
1. Feature Extraction & Normalization
2. DBSCAN Density Clustering & Outlier Detection
3. 2D PCA Dimensionality Reduction
4. Exact FAISS CPU Similarity Indexing
"""

from __future__ import annotations

import json
import os
import sys
from typing import Any, Dict
import numpy as np
import pandas as pd

# Ensure research-ml root is on sys.path
CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
RESEARCH_ML_DIR = os.path.abspath(os.path.join(CURRENT_DIR, ".."))
if RESEARCH_ML_DIR not in sys.path:
    sys.path.insert(0, RESEARCH_ML_DIR)

from cohort.clustering.dbscan.clusterer import CohortDBSCANClusterer
from cohort.dimensionality_reduction.pca.reducer import CohortPCAReducer
from cohort.features.builder import CohortFeatureBuilder
from cohort.similarity.faiss.indexer import CohortFaissIndexer


def run_full_cohort_pipeline(
    data_path: Optional[str] = None,
    output_base_dir: Optional[str] = None,
    eps: float = 0.6,
    min_samples: int = 10,
) -> Dict[str, Any]:
    """Executes the 4-stage Cohort Atlas pipeline and persists all research artifacts."""
    if data_path is None:
        data_path = os.path.abspath(
            os.path.join(RESEARCH_ML_DIR, "dropout", "data", "features_horizon_90_cutoff_30.csv")
        )
    if output_base_dir is None:
        output_base_dir = CURRENT_DIR

    feat_dir = os.path.join(output_base_dir, "features")
    dbscan_dir = os.path.join(output_base_dir, "clustering", "dbscan")
    pca_dir = os.path.join(output_base_dir, "dimensionality_reduction", "pca")
    faiss_dir = os.path.join(output_base_dir, "similarity", "faiss")

    print(f"[Cohort Atlas] Loading cohort dataset: {data_path}")
    df = pd.read_csv(data_path)
    print(f"[Cohort Atlas] Input records: {len(df)}")

    # 1. Feature Extraction & Normalization
    print("\n--- Step 1: Building Standardized Feature Matrix ---")
    builder = CohortFeatureBuilder()
    p_ids, X_raw, X_scaled = builder.fit_transform(df)
    feat_artifacts = builder.save_artifacts(feat_dir, p_ids, X_scaled)
    print(f"Feature matrix shape: {X_scaled.shape}")
    print(f"Artifacts saved in: {feat_dir}")

    # 2. DBSCAN Clustering
    print(f"\n--- Step 2: DBSCAN Clustering (eps={eps}, min_samples={min_samples}) ---")
    clusterer = CohortDBSCANClusterer(eps=eps, min_samples=min_samples)
    labels = clusterer.fit_predict(
        participant_ids=p_ids,
        X_scaled=X_scaled,
        feature_names=builder.feature_names,
        X_raw=X_raw,
    )
    dbscan_artifacts = clusterer.save_artifacts(dbscan_dir, p_ids)
    n_clusters = clusterer.metadata["n_clusters"]
    n_noise = clusterer.metadata["n_noise_points"]
    print(f"Clusters discovered (excluding noise): {n_clusters}")
    print(f"Outlier noise points: {n_noise} ({clusterer.metadata['noise_percentage']:.1%})")
    print(f"Cluster sizes: {clusterer.metadata['cluster_sizes']}")

    # 3. PCA Dimensionality Reduction
    print("\n--- Step 3: 2D PCA Dimensionality Reduction ---")
    reducer = CohortPCAReducer(n_components=2, random_state=42)
    proj_df = reducer.fit_transform(
        X_scaled=X_scaled,
        participant_ids=p_ids,
        cluster_labels=labels.tolist(),
        feature_names=builder.feature_names,
    )
    pca_artifacts = reducer.save_artifacts(pca_dir, proj_df)
    exp_var = reducer.metadata["explained_variance_ratio"]
    cum_var = reducer.metadata["cumulative_explained_variance"]
    print(f"Explained Variance: PC1 = {exp_var['PC1']:.2%}, PC2 = {exp_var['PC2']:.2%} (Cumulative = {cum_var:.2%})")

    # 4. FAISS Exact CPU Similarity Indexing
    print("\n--- Step 4: FAISS Exact CPU Similarity Indexing ---")
    indexer = CohortFaissIndexer()
    indexer.build_index(participant_ids=p_ids, feature_matrix=X_scaled)
    faiss_artifacts = indexer.save_artifacts(faiss_dir)
    print(f"FAISS index built: {indexer.metadata['index_type']} with {indexer.metadata['n_total_indexed']} vectors (dim={indexer.dimension})")

    # Example Query for the first participant
    sample_pid = p_ids[0]
    sample_neighbors = indexer.query_by_participant_id(sample_pid, k=3, include_self=False)
    print(f"\nExample Nearest Neighbors for {sample_pid}:")
    for n in sample_neighbors:
        print(f"  Rank {n['rank']}: ID={n['participant_id']}, L2 Distance={n['l2_distance']:.4f}, Similarity={n['similarity_score']:.4f}")

    return {
        "feature_matrix_shape": list(X_scaled.shape),
        "n_clusters": n_clusters,
        "n_noise_points": n_noise,
        "cluster_sizes": clusterer.metadata["cluster_sizes"],
        "explained_variance_ratio": exp_var,
        "cumulative_explained_variance": cum_var,
        "faiss_index_type": indexer.metadata["index_type"],
        "faiss_total_indexed": indexer.metadata["n_total_indexed"],
        "sample_query": {
            "query_participant_id": sample_pid,
            "top_neighbors": sample_neighbors,
        },
        "artifacts": {
            "features": feat_artifacts,
            "dbscan": dbscan_artifacts,
            "pca": pca_artifacts,
            "faiss": faiss_artifacts,
        },
    }


if __name__ == "__main__":
    results = run_full_cohort_pipeline()
    print("\n[Cohort Atlas] Pipeline completed successfully.")
