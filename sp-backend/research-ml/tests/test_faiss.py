"""Unit tests for CohortFaissIndexer (Phase R6)."""

from __future__ import annotations

import os
import tempfile
import numpy as np
import pytest

from cohort.similarity.faiss.indexer import CohortFaissIndexer


@pytest.fixture
def index_data():
    """Generates synthetic normalized vectors for FAISS indexing."""
    rng = np.random.RandomState(42)
    n = 50
    d = 20
    X = rng.normal(0.0, 1.0, (n, d)).astype(np.float32)
    ids = [f"PARTICIPANT-{i:03d}" for i in range(n)]
    return ids, X


def test_faiss_index_creation_and_self_query(index_data):
    """Validates building exact CPU index and querying top neighbors."""
    ids, X = index_data
    indexer = CohortFaissIndexer()
    indexer.build_index(ids, X)

    assert indexer.index is not None
    assert indexer.metadata["n_total_indexed"] == len(ids)
    assert indexer.dimension == X.shape[1]

    # Query with include_self=True: rank 1 must be the exact participant with distance ~0
    target_id = ids[5]
    results_with_self = indexer.query_by_participant_id(target_id, k=3, include_self=True)

    assert len(results_with_self) == 3
    assert results_with_self[0]["participant_id"] == target_id
    assert results_with_self[0]["rank"] == 1
    assert pytest.approx(results_with_self[0]["l2_distance"], abs=1e-5) == 0.0
    assert pytest.approx(results_with_self[0]["similarity_score"], abs=1e-5) == 1.0

    # Query with include_self=False: target_id must be excluded
    results_no_self = indexer.query_by_participant_id(target_id, k=3, include_self=False)
    assert len(results_no_self) == 3
    assert all(r["participant_id"] != target_id for r in results_no_self)


def test_faiss_save_and_load(index_data):
    """Validates serializing and deserializing index with ID mappings."""
    ids, X = index_data
    indexer = CohortFaissIndexer()
    indexer.build_index(ids, X)

    target_id = ids[2]
    orig_results = indexer.query_by_participant_id(target_id, k=3, include_self=False)

    with tempfile.TemporaryDirectory() as tmp_dir:
        saved = indexer.save_artifacts(tmp_dir)

        assert os.path.exists(saved["index_path"])
        assert os.path.exists(saved["mapping_path"])
        assert os.path.exists(saved["metadata_path"])

        loaded_indexer = CohortFaissIndexer.load(tmp_dir)
        loaded_results = loaded_indexer.query_by_participant_id(target_id, k=3, include_self=False)

        assert len(loaded_results) == len(orig_results)
        for r_orig, r_load in zip(orig_results, loaded_results):
            assert r_orig["participant_id"] == r_load["participant_id"]
            assert pytest.approx(r_orig["l2_distance"], rel=1e-4) == r_load["l2_distance"]
