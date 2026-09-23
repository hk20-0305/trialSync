"""FAISS Exact CPU Patient Similarity Index for TrialSync Cohort Atlas (Phase R6).

Builds an exact CPU index (IndexFlatL2) on normalized clinical feature vectors,
maintains bidirectional participant ID mappings, and provides nearest-neighbor search.
"""

from __future__ import annotations

import json
import os
from typing import Any, Dict, List, Optional, Tuple, Union
import faiss
import numpy as np


class CohortFaissIndexer:
    """Exact CPU similarity search index for matching clinical participant trajectories."""

    def __init__(self, dimension: Optional[int] = None):
        self.dimension = dimension
        self.index: Optional[faiss.IndexFlatL2] = None
        self.idx_to_id: Dict[int, str] = {}
        self.id_to_idx: Dict[str, int] = {}
        self.metadata: Dict[str, Any] = {}

    def build_index(
        self,
        participant_ids: List[str],
        feature_matrix: np.ndarray,
    ) -> "CohortFaissIndexer":
        """Builds exact IndexFlatL2 on float32 feature matrix and registers ID mappings."""
        n_samples, dim = feature_matrix.shape
        self.dimension = dim

        # Ensure float32 and contiguous memory layout
        X_float32 = np.ascontiguousarray(feature_matrix, dtype=np.float32)

        # Build exact L2 CPU index
        self.index = faiss.IndexFlatL2(dim)
        self.index.add(X_float32)

        self.idx_to_id = {i: pid for i, pid in enumerate(participant_ids)}
        self.id_to_idx = {pid: i for i, pid in enumerate(participant_ids)}

        self.metadata = {
            "index_type": "IndexFlatL2",
            "metric": "L2_EUCLIDEAN",
            "dimension": dim,
            "n_total_indexed": self.index.ntotal,
        }

        return self

    def query_by_vector(
        self, query_vec: np.ndarray, k: int = 5
    ) -> List[Dict[str, Any]]:
        """Finds top-k nearest neighbor participants for an arbitrary feature vector."""
        if self.index is None:
            raise RuntimeError("FAISS index must be built before querying.")

        q_mat = np.ascontiguousarray(query_vec.reshape(1, -1), dtype=np.float32)
        distances, indices = self.index.search(q_mat, k)

        results = []
        for rank, (idx, dist) in enumerate(zip(indices[0], distances[0]), start=1):
            if idx in self.idx_to_id:
                pid = self.idx_to_id[idx]
                # Convert L2 distance to intuitive similarity: 1 / (1 + distance)
                similarity = round(float(1.0 / (1.0 + dist)), 5)
                results.append(
                    {
                        "rank": rank,
                        "participant_id": pid,
                        "faiss_index": int(idx),
                        "l2_distance": round(float(dist), 5),
                        "similarity_score": similarity,
                    }
                )

        return results

    def query_by_participant_id(
        self, participant_id: str, k: int = 5, include_self: bool = False
    ) -> List[Dict[str, Any]]:
        """Finds top-k most similar cohort peers for a known participant ID."""
        if self.index is None:
            raise RuntimeError("FAISS index must be built before querying.")

        if participant_id not in self.id_to_idx:
            raise KeyError(f"Participant ID '{participant_id}' not present in index.")

        target_idx = self.id_to_idx[participant_id]
        # Reconstruct stored vector from index
        query_vec = self.index.reconstruct(target_idx)

        # Query k + 1 neighbors to handle self-exclusion
        raw_results = self.query_by_vector(query_vec, k=k + 1)

        filtered = []
        rank_counter = 1
        for res in raw_results:
            if not include_self and res["participant_id"] == participant_id:
                continue
            res["rank"] = rank_counter
            filtered.append(res)
            rank_counter += 1
            if len(filtered) == k:
                break

        return filtered

    def save_artifacts(self, output_dir: str) -> Dict[str, str]:
        """Serializes the FAISS binary index, ID mapping table, and index metadata."""
        if self.index is None:
            raise RuntimeError("Cannot save uninitialized index.")

        os.makedirs(output_dir, exist_ok=True)

        index_path = os.path.join(output_dir, "cohort.index")
        mapping_path = os.path.join(output_dir, "id_mapping.json")
        meta_path = os.path.join(output_dir, "index_metadata.json")

        # Save FAISS binary index
        faiss.write_index(self.index, index_path)

        # Save ID mappings
        with open(mapping_path, "w", encoding="utf-8") as f:
            json.dump(
                {
                    "idx_to_id": {str(k): v for k, v in self.idx_to_id.items()},
                    "id_to_idx": self.id_to_idx,
                },
                f,
                indent=2,
            )

        # Save metadata
        with open(meta_path, "w", encoding="utf-8") as f:
            json.dump(self.metadata, f, indent=2)

        return {
            "index_path": index_path,
            "mapping_path": mapping_path,
            "metadata_path": meta_path,
        }

    @classmethod
    def load(cls, index_dir: str) -> "CohortFaissIndexer":
        """Loads a persisted FAISS index and ID mappings from disk."""
        index_path = os.path.join(index_dir, "cohort.index")
        mapping_path = os.path.join(index_dir, "id_mapping.json")
        meta_path = os.path.join(index_dir, "index_metadata.json")

        if not os.path.exists(index_path) or not os.path.exists(mapping_path):
            raise FileNotFoundError(f"Missing FAISS index or mapping in: {index_dir}")

        instance = cls()
        instance.index = faiss.read_index(index_path)
        instance.dimension = instance.index.d

        with open(mapping_path, "r", encoding="utf-8") as f:
            mapping_data = json.load(f)
            instance.idx_to_id = {int(k): v for k, v in mapping_data["idx_to_id"].items()}
            instance.id_to_idx = mapping_data["id_to_idx"]

        if os.path.exists(meta_path):
            with open(meta_path, "r", encoding="utf-8") as f:
                instance.metadata = json.load(f)

        return instance
