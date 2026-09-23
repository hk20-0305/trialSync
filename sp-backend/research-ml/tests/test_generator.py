"""Unit tests for the LongitudinalDataGenerator (Phase R3)."""

from __future__ import annotations

import os
import tempfile
import pytest

from dropout.data.generator import LongitudinalDataGenerator


def test_deterministic_reproducibility():
    """Validates that identical seeds yield identical participant cohorts."""
    gen1 = LongitudinalDataGenerator(seed=42)
    gen2 = LongitudinalDataGenerator(seed=42)

    parts1, enrs1, doses1, visits1, meas1, aes1, outs1 = gen1.generate_cohort(num_participants=50)
    parts2, enrs2, doses2, visits2, meas2, aes2, outs2 = gen2.generate_cohort(num_participants=50)

    assert len(parts1) == len(parts2) == 50
    assert len(enrs1) == len(enrs2) == 50
    assert len(doses1) == len(doses2)
    assert len(visits1) == len(visits2)
    assert len(meas1) == len(meas2)
    assert len(aes1) == len(aes2)
    assert len(outs1) == len(outs2)

    for p1, p2 in zip(parts1, parts2):
        assert p1.participant_code == p2.participant_code
        assert p1.age == p2.age
        assert p1.sex == p2.sex
        assert p1.site_id == p2.site_id

    for o1, o2 in zip(outs1, outs2):
        assert o1.dropout_within_horizon == o2.dropout_within_horizon
        assert o1.dropout_day == o2.dropout_day
        assert o1.follow_up_days == o2.follow_up_days


def test_seed_variance():
    """Validates that different seeds generate distinct synthetic datasets."""
    gen1 = LongitudinalDataGenerator(seed=42)
    gen2 = LongitudinalDataGenerator(seed=123)

    parts1, _, _, _, _, _, outs1 = gen1.generate_cohort(num_participants=50)
    parts2, _, _, _, _, _, outs2 = gen2.generate_cohort(num_participants=50)

    ages1 = [p.age for p in parts1]
    ages2 = [p.age for p in parts2]
    assert ages1 != ages2

    dropouts1 = [o.dropout_within_horizon for o in outs1]
    dropouts2 = [o.dropout_within_horizon for o in outs2]
    assert dropouts1 != dropouts2


def test_cohort_prevalence_and_integrity():
    """Validates that synthetic dropouts and non-dropouts exist within realistic ranges."""
    gen = LongitudinalDataGenerator(seed=42)
    parts, enrs, doses, visits, meas, aes, outs = gen.generate_cohort(num_participants=200)

    dropouts = [o for o in outs if o.dropout_within_horizon]
    dropout_rate = len(dropouts) / len(outs)

    # Expected ~20-30% dropout prevalence
    assert 0.15 <= dropout_rate <= 0.35

    # Check linkage integrity
    part_ids = {p.id for p in parts}
    for e in enrs:
        assert e.participant_id in part_ids

    enr_ids = {e.id for e in enrs}
    for d in doses:
        assert d.enrollment_id in enr_ids
    for v in visits:
        assert v.enrollment_id in enr_ids
    for m in meas:
        assert m.enrollment_id in enr_ids
    for a in aes:
        assert a.enrollment_id in enr_ids
    for o in outs:
        assert o.enrollment_id in enr_ids


def test_time_ordering_and_no_future_leakage():
    """Validates that events do not occur beyond participant follow-up duration."""
    gen = LongitudinalDataGenerator(seed=42)
    _, _, doses, visits, meas, aes, outs = gen.generate_cohort(num_participants=100)

    follow_up_map = {o.enrollment_id: o.follow_up_days for o in outs}

    for d in doses:
        assert d.scheduled_day <= follow_up_map[d.enrollment_id]

    for v in visits:
        assert v.scheduled_day <= follow_up_map[v.enrollment_id]

    for m in meas:
        assert m.measurement_day <= follow_up_map[m.enrollment_id]

    for a in aes:
        assert a.event_day <= follow_up_map[a.enrollment_id]


def test_save_cohort_to_csv():
    """Validates that CSV export writes all 7 files with valid headers and data."""
    gen = LongitudinalDataGenerator(seed=42)
    with tempfile.TemporaryDirectory() as tmp_dir:
        saved = gen.save_cohort_to_csv(output_dir=tmp_dir, num_participants=30)
        assert len(saved) == 7
        for name, path in saved.items():
            assert os.path.exists(path)
            assert os.path.getsize(path) > 100
