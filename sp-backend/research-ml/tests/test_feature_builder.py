"""Unit tests for FixedHorizonFeatureBuilder (Phase R3)."""

from __future__ import annotations

import copy
import os
import tempfile
import pytest

from dropout.data.generator import LongitudinalDataGenerator
from dropout.features.builder import FixedHorizonFeatureBuilder


def test_feature_builder_output_shape():
    """Validates that the feature builder generates the expected columns without NaNs."""
    gen = LongitudinalDataGenerator(seed=42)
    with tempfile.TemporaryDirectory() as tmp_dir:
        gen.save_cohort_to_csv(output_dir=tmp_dir, num_participants=50)

        builder = FixedHorizonFeatureBuilder(observation_cutoff_day=30, prediction_horizon_day=90)
        features = builder.build_from_csv_dir(data_dir=tmp_dir)

        assert len(features) == 50
        sample = features[0]

        expected_keys = [
            "enrollment_id",
            "participant_id",
            "age",
            "is_female",
            "is_experimental_arm",
            "adherence_ratio_pre_cutoff",
            "doses_missed_pre_cutoff",
            "visit_attendance_rate_pre_cutoff",
            "ae_count_pre_cutoff",
            "ae_burden_score_pre_cutoff",
            "abnormal_measurement_rate_pre_cutoff",
            "systolic_bp_baseline",
            "platelets_latest_pre_cutoff",
            "alt_latest_pre_cutoff",
            "dropout_within_horizon",
        ]

        for k in expected_keys:
            assert k in sample
            assert sample[k] is not None


def test_strict_cutoff_isolation():
    """Validates that events occurring after the observation cutoff (day 30) do NOT affect pre-cutoff features."""
    builder = FixedHorizonFeatureBuilder(observation_cutoff_day=30, prediction_horizon_day=90)

    p = [{"id": "p1", "participant_code": "RP-001", "site_id": "SITE-01", "birth_year": 1980, "sex": "FEMALE", "demographics": "{}"}]
    e = [{"id": "e1", "participant_id": "p1", "enrollment_code": "ENR-001", "arm": "EXPERIMENTAL", "status": "COMPLETED"}]
    o = [{"id": "o1", "enrollment_id": "e1", "horizon_days": 90, "dropout_within_horizon": False, "dropout_day": None, "dropout_reason": None, "follow_up_days": 90}]

    doses_base = [
        {"id": "d1", "enrollment_id": "e1", "scheduled_day": 7, "prescribed_dose_amount": 100.0, "actual_dose_amount": 100.0, "status": "ADMINISTERED"},
        {"id": "d2", "enrollment_id": "e1", "scheduled_day": 14, "prescribed_dose_amount": 100.0, "actual_dose_amount": 100.0, "status": "ADMINISTERED"},
    ]

    # Baseline features with only pre-cutoff doses
    feats1 = builder.build_features_from_records(
        participants=p,
        enrollments=e,
        dose_events=doses_base,
        visit_events=[],
        measurements=[],
        adverse_events=[],
        outcomes=o,
    )

    # Now add a post-cutoff missed dose on day 45
    doses_with_future_event = copy.deepcopy(doses_base)
    doses_with_future_event.append(
        {"id": "d3", "enrollment_id": "e1", "scheduled_day": 45, "prescribed_dose_amount": 100.0, "actual_dose_amount": 0.0, "status": "MISSED"}
    )

    # Also add a post-cutoff severe AE on day 50
    future_aes = [
        {"id": "a1", "enrollment_id": "e1", "event_day": 50, "ctcae_grade": 4, "is_serious": True, "relatedness": "DEFINITELY_RELATED"}
    ]

    feats2 = builder.build_features_from_records(
        participants=p,
        enrollments=e,
        dose_events=doses_with_future_event,
        visit_events=[],
        measurements=[],
        adverse_events=future_aes,
        outcomes=o,
    )

    # Features at cutoff day 30 MUST be identical
    assert feats1[0]["doses_scheduled_pre_cutoff"] == feats2[0]["doses_scheduled_pre_cutoff"]
    assert feats1[0]["doses_missed_pre_cutoff"] == feats2[0]["doses_missed_pre_cutoff"] == 0
    assert feats1[0]["adherence_ratio_pre_cutoff"] == feats2[0]["adherence_ratio_pre_cutoff"] == 1.0
    assert feats1[0]["ae_count_pre_cutoff"] == feats2[0]["ae_count_pre_cutoff"] == 0
    assert feats1[0]["ae_max_grade_pre_cutoff"] == feats2[0]["ae_max_grade_pre_cutoff"] == 0


def test_scenario_responsiveness():
    """Validates that changing a pre-cutoff dose from administered to missed updates adherence features."""
    builder = FixedHorizonFeatureBuilder(observation_cutoff_day=30, prediction_horizon_day=90)

    p = [{"id": "p1", "participant_code": "RP-001", "site_id": "SITE-01", "birth_year": 1980, "sex": "FEMALE", "demographics": "{}"}]
    e = [{"id": "e1", "participant_id": "p1", "enrollment_code": "ENR-001", "arm": "EXPERIMENTAL", "status": "COMPLETED"}]
    o = [{"id": "o1", "enrollment_id": "e1", "horizon_days": 90, "dropout_within_horizon": False, "dropout_day": None, "dropout_reason": None, "follow_up_days": 90}]

    doses_perfect = [
        {"id": "d1", "enrollment_id": "e1", "scheduled_day": 7, "prescribed_dose_amount": 100.0, "actual_dose_amount": 100.0, "status": "ADMINISTERED"},
        {"id": "d2", "enrollment_id": "e1", "scheduled_day": 14, "prescribed_dose_amount": 100.0, "actual_dose_amount": 100.0, "status": "ADMINISTERED"},
    ]

    feats_perfect = builder.build_features_from_records(
        participants=p, enrollments=e, dose_events=doses_perfect, visit_events=[], measurements=[], adverse_events=[], outcomes=o
    )

    doses_missed = [
        {"id": "d1", "enrollment_id": "e1", "scheduled_day": 7, "prescribed_dose_amount": 100.0, "actual_dose_amount": 100.0, "status": "ADMINISTERED"},
        {"id": "d2", "enrollment_id": "e1", "scheduled_day": 14, "prescribed_dose_amount": 100.0, "actual_dose_amount": 0.0, "status": "MISSED"},
    ]

    feats_missed = builder.build_features_from_records(
        participants=p, enrollments=e, dose_events=doses_missed, visit_events=[], measurements=[], adverse_events=[], outcomes=o
    )

    assert feats_perfect[0]["doses_missed_pre_cutoff"] == 0
    assert feats_perfect[0]["adherence_ratio_pre_cutoff"] == 1.0

    assert feats_missed[0]["doses_missed_pre_cutoff"] == 1
    assert feats_missed[0]["adherence_ratio_pre_cutoff"] == 0.5
