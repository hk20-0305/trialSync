"""Longitudinal Synthetic Research Data Generator for TrialSync (Phase R3).

Generates reproducible, event-level synthetic clinical trial datasets conforming to the
Phase R3 research relational schema:
- participants
- enrollments
- dose_events
- visit_events
- measurements
- adverse_events
- outcomes
"""

from __future__ import annotations

import csv
import json
import os
import random
import uuid
from dataclasses import asdict, dataclass
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, List, Optional, Tuple


@dataclass
class ParticipantRecord:
    id: str
    participant_code: str
    site_id: str
    birth_year: int
    age: int
    sex: str
    demographics: str
    created_at: str
    updated_at: str


@dataclass
class EnrollmentRecord:
    id: str
    participant_id: str
    enrollment_code: str
    enrolled_at: str
    arm: str
    status: str
    created_at: str
    updated_at: str


@dataclass
class DoseEventRecord:
    id: str
    enrollment_id: str
    dose_number: int
    scheduled_day: int
    scheduled_at: str
    administered_at: Optional[str]
    prescribed_dose_amount: float
    actual_dose_amount: Optional[float]
    unit: str
    status: str
    adherence_ratio: Optional[float]
    notes: Optional[str]
    created_at: str
    updated_at: str


@dataclass
class VisitEventRecord:
    id: str
    enrollment_id: str
    visit_number: int
    visit_name: str
    scheduled_day: int
    actual_day: Optional[int]
    scheduled_at: str
    attended_at: Optional[str]
    status: str
    visit_type: str
    notes: Optional[str]
    created_at: str
    updated_at: str


@dataclass
class MeasurementRecord:
    id: str
    enrollment_id: str
    visit_event_id: Optional[str]
    measurement_day: int
    measured_at: str
    code: str
    name: str
    numeric_value: float
    text_value: Optional[str]
    unit: str
    reference_range_low: float
    reference_range_high: float
    is_abnormal: bool
    created_at: str
    updated_at: str


@dataclass
class AdverseEventRecord:
    id: str
    enrollment_id: str
    event_day: int
    onset_at: str
    resolved_at: Optional[str]
    term: str
    ctcae_grade: int
    is_serious: bool
    relatedness: str
    action_taken: str
    outcome: str
    notes: Optional[str]
    created_at: str
    updated_at: str


@dataclass
class OutcomeRecord:
    id: str
    enrollment_id: str
    horizon_days: int
    dropout_within_horizon: bool
    dropout_day: Optional[int]
    dropout_reason: Optional[str]
    is_censored: bool
    censoring_day: Optional[int]
    follow_up_days: int
    completed_study: bool
    notes: Optional[str]
    created_at: str
    updated_at: str


class LongitudinalDataGenerator:
    """Generates synthetic longitudinal clinical trial datasets."""

    def __init__(self, seed: int = 42):
        self.seed = seed
        self.rng = random.Random(seed)

        self.sites = ["SITE-01", "SITE-02", "SITE-03", "SITE-04", "SITE-05"]
        self.arms = ["EXPERIMENTAL", "STANDARD_CARE"]
        self.ae_terms = [
            ("Nausea", 1, 2, "POSSIBLY_RELATED"),
            ("Fatigue", 1, 3, "PROBABLY_RELATED"),
            ("Neutropenia", 2, 4, "DEFINITELY_RELATED"),
            ("Rash", 1, 2, "POSSIBLY_RELATED"),
            ("Headache", 1, 2, "NOT_RELATED"),
            ("Elevated ALT", 2, 4, "PROBABLY_RELATED"),
            ("Hypertension", 1, 3, "POSSIBLY_RELATED"),
            ("Diarrhea", 1, 3, "POSSIBLY_RELATED"),
        ]

        self.dose_schedule_days = [1, 7, 14, 21, 28, 35, 42, 49, 56, 63, 70, 77, 84]
        self.visit_schedule = [
            (1, "BASELINE", 0, "ON_SITE"),
            (2, "WEEK_2", 14, "ON_SITE"),
            (3, "MONTH_1", 30, "ON_SITE"),
            (4, "MONTH_2", 60, "REMOTE"),
            (5, "MONTH_3_END", 90, "ON_SITE"),
        ]

    def generate_cohort(
        self,
        num_participants: int = 400,
        start_date: Optional[datetime] = None,
    ) -> Tuple[
        List[ParticipantRecord],
        List[EnrollmentRecord],
        List[DoseEventRecord],
        List[VisitEventRecord],
        List[MeasurementRecord],
        List[AdverseEventRecord],
        List[OutcomeRecord],
    ]:
        """Generates all 7 tables for a cohort of synthetic participants."""
        if start_date is None:
            start_date = datetime(2026, 1, 1, 9, 0, 0, tzinfo=timezone.utc)

        participants: List[ParticipantRecord] = []
        enrollments: List[EnrollmentRecord] = []
        all_dose_events: List[DoseEventRecord] = []
        all_visit_events: List[VisitEventRecord] = []
        all_measurements: List[MeasurementRecord] = []
        all_adverse_events: List[AdverseEventRecord] = []
        all_outcomes: List[OutcomeRecord] = []

        for i in range(1, num_participants + 1):
            p_id = str(uuid.UUID(int=self.rng.getrandbits(128), version=4))
            e_id = str(uuid.UUID(int=self.rng.getrandbits(128), version=4))

            code_p = f"RP-{i:04d}"
            code_e = f"ENR-{i:04d}"

            site = self.rng.choice(self.sites)
            birth_year = self.rng.randint(1955, 2002)
            age = 2026 - birth_year
            sex = "FEMALE" if self.rng.random() < 0.52 else "MALE"
            arm = "EXPERIMENTAL" if self.rng.random() < 0.60 else "STANDARD_CARE"

            # Latent vulnerability factors
            frailty = self.rng.uniform(0.0, 1.0)
            baseline_health_score = round(100.0 - (age * 0.4) - (frailty * 20.0), 1)
            travel_burden = self.rng.choice(["LOW", "MEDIUM", "HIGH"])

            demographics_json = json.dumps(
                {
                    "baseline_health_score": baseline_health_score,
                    "travel_burden": travel_burden,
                    "frailty_index": round(frailty, 3),
                }
            )

            now_iso = start_date.isoformat()

            part = ParticipantRecord(
                id=p_id,
                participant_code=code_p,
                site_id=site,
                birth_year=birth_year,
                age=age,
                sex=sex,
                demographics=demographics_json,
                created_at=now_iso,
                updated_at=now_iso,
            )
            participants.append(part)

            # Determine dropout trajectory stochastically
            # Baseline hazard influenced by age, arm, frailty, travel burden
            hazard_score = (
                0.08
                + (frailty * 0.22)
                + (0.08 if arm == "EXPERIMENTAL" else 0.02)
                + (0.06 if travel_burden == "HIGH" else 0.0)
                + (0.04 if age > 65 else 0.0)
            )

            will_dropout = self.rng.random() < hazard_score
            dropout_day: Optional[int] = None
            dropout_reason: Optional[str] = None
            follow_up_days: int = 90

            if will_dropout:
                # Dropout day between 15 and 89
                dropout_day = self.rng.randint(15, 89)
                follow_up_days = dropout_day
                if frailty > 0.6:
                    dropout_reason = self.rng.choice(["ADVERSE_EVENT", "LACK_OF_EFFICACY"])
                elif travel_burden == "HIGH":
                    dropout_reason = self.rng.choice(["WITHDRAWAL_BY_SUBJECT", "LOST_TO_FOLLOW_UP"])
                else:
                    dropout_reason = self.rng.choice(
                        ["ADVERSE_EVENT", "WITHDRAWAL_BY_SUBJECT", "PROTOCOL_VIOLATION"]
                    )

            enr_status = "DROPPED_OUT" if will_dropout else "COMPLETED"
            enr = EnrollmentRecord(
                id=e_id,
                participant_id=p_id,
                enrollment_code=code_e,
                enrolled_at=now_iso,
                arm=arm,
                status=enr_status,
                created_at=now_iso,
                updated_at=now_iso,
            )
            enrollments.append(enr)

            # Generate Doses
            prescribed_dose = 100.0 if arm == "EXPERIMENTAL" else 50.0
            for d_idx, day in enumerate(self.dose_schedule_days, start=1):
                if day > follow_up_days:
                    break

                dose_id = str(uuid.UUID(int=self.rng.getrandbits(128), version=4))
                dose_time = start_date + timedelta(days=day, hours=9)

                # Missed dose probability higher for future dropouts / high travel burden
                miss_prob = 0.04 + (0.15 if will_dropout else 0.0) + (0.05 if travel_burden == "HIGH" else 0.0)
                is_missed = self.rng.random() < miss_prob
                is_reduced = (not is_missed) and (self.rng.random() < 0.06)

                if is_missed:
                    status = "MISSED"
                    actual_amount = 0.0
                    adherence = 0.0
                    admin_time = None
                elif is_reduced:
                    status = "REDUCED"
                    actual_amount = prescribed_dose * 0.5
                    adherence = 0.5
                    admin_time = dose_time.isoformat()
                else:
                    status = "ADMINISTERED"
                    actual_amount = prescribed_dose
                    adherence = 1.0
                    admin_time = dose_time.isoformat()

                all_dose_events.append(
                    DoseEventRecord(
                        id=dose_id,
                        enrollment_id=e_id,
                        dose_number=d_idx,
                        scheduled_day=day,
                        scheduled_at=dose_time.isoformat(),
                        administered_at=admin_time,
                        prescribed_dose_amount=prescribed_dose,
                        actual_dose_amount=actual_amount,
                        unit="mg",
                        status=status,
                        adherence_ratio=adherence,
                        notes=f"Dose {d_idx} on day {day}",
                        created_at=now_iso,
                        updated_at=now_iso,
                    )
                )

            # Generate Visits & Measurements
            visit_id_map: Dict[int, str] = {}
            for v_num, v_name, s_day, v_type in self.visit_schedule:
                if s_day > follow_up_days:
                    break

                v_id = str(uuid.UUID(int=self.rng.getrandbits(128), version=4))
                visit_id_map[s_day] = v_id
                v_sched_time = start_date + timedelta(days=s_day, hours=10)

                # Visit attendance
                miss_v_prob = 0.03 + (0.14 if will_dropout else 0.0)
                is_v_missed = (s_day > 0) and (self.rng.random() < miss_v_prob)

                if is_v_missed:
                    v_status = "MISSED"
                    actual_day = None
                    v_attended_time = None
                else:
                    v_status = "ATTENDED"
                    day_offset = self.rng.choice([0, 0, 0, 1, -1]) if s_day > 0 else 0
                    actual_day = min(follow_up_days, max(0, s_day + day_offset))
                    v_attended_time = (start_date + timedelta(days=actual_day, hours=10)).isoformat()

                all_visit_events.append(
                    VisitEventRecord(
                        id=v_id,
                        enrollment_id=e_id,
                        visit_number=v_num,
                        visit_name=v_name,
                        scheduled_day=s_day,
                        actual_day=actual_day,
                        scheduled_at=v_sched_time.isoformat(),
                        attended_at=v_attended_time,
                        status=v_status,
                        visit_type=v_type,
                        notes=f"Visit {v_name}",
                        created_at=now_iso,
                        updated_at=now_iso,
                    )
                )

                # Measurements on attended visits
                if v_status == "ATTENDED":
                    m_day = actual_day if actual_day is not None else s_day
                    m_time = (start_date + timedelta(days=m_day, hours=11)).isoformat()

                    # 1. Systolic BP (mmHg: 90 - 140 normal)
                    base_sbp = 120.0 + (frailty * 15.0) + (self.rng.gauss(0, 8.0))
                    sbp_val = round(base_sbp + (s_day * 0.1 if will_dropout else 0.0), 1)
                    sbp_abnormal = sbp_val < 90.0 or sbp_val > 140.0
                    all_measurements.append(
                        MeasurementRecord(
                            id=str(uuid.UUID(int=self.rng.getrandbits(128), version=4)),
                            enrollment_id=e_id,
                            visit_event_id=v_id,
                            measurement_day=m_day,
                            measured_at=m_time,
                            code="systolic_bp",
                            name="Systolic Blood Pressure",
                            numeric_value=sbp_val,
                            text_value=None,
                            unit="mmHg",
                            reference_range_low=90.0,
                            reference_range_high=140.0,
                            is_abnormal=sbp_abnormal,
                            created_at=now_iso,
                            updated_at=now_iso,
                        )
                    )

                    # 2. Platelets (x10^9/L: 150 - 450 normal)
                    base_plt = 250.0 - (frailty * 60.0) + (self.rng.gauss(0, 30.0))
                    plt_val = round(max(50.0, base_plt - (s_day * 0.6 if will_dropout else 0.0)), 1)
                    plt_abnormal = plt_val < 150.0 or plt_val > 450.0
                    all_measurements.append(
                        MeasurementRecord(
                            id=str(uuid.UUID(int=self.rng.getrandbits(128), version=4)),
                            enrollment_id=e_id,
                            visit_event_id=v_id,
                            measurement_day=m_day,
                            measured_at=m_time,
                            code="platelets",
                            name="Platelet Count",
                            numeric_value=plt_val,
                            text_value=None,
                            unit="x10^9/L",
                            reference_range_low=150.0,
                            reference_range_high=450.0,
                            is_abnormal=plt_abnormal,
                            created_at=now_iso,
                            updated_at=now_iso,
                        )
                    )

                    # 3. ALT (U/L: 7 - 56 normal)
                    base_alt = 25.0 + (frailty * 18.0) + (self.rng.gauss(0, 6.0))
                    alt_val = round(max(5.0, base_alt + (s_day * 0.35 if will_dropout else 0.0)), 1)
                    alt_abnormal = alt_val < 7.0 or alt_val > 56.0
                    all_measurements.append(
                        MeasurementRecord(
                            id=str(uuid.UUID(int=self.rng.getrandbits(128), version=4)),
                            enrollment_id=e_id,
                            visit_event_id=v_id,
                            measurement_day=m_day,
                            measured_at=m_time,
                            code="alt",
                            name="Alanine Aminotransferase",
                            numeric_value=alt_val,
                            text_value=None,
                            unit="U/L",
                            reference_range_low=7.0,
                            reference_range_high=56.0,
                            is_abnormal=alt_abnormal,
                            created_at=now_iso,
                            updated_at=now_iso,
                        )
                    )

            # Generate Adverse Events
            # Higher frequency if frailty or experimental arm
            ae_rate = 0.4 + (frailty * 0.8) + (0.3 if arm == "EXPERIMENTAL" else 0.0)
            num_aes = self.rng.poisson(ae_rate) if hasattr(self.rng, "poisson") else int(self.rng.expovariate(1.0 / max(0.1, ae_rate)))
            num_aes = min(num_aes, 4)

            for _ in range(num_aes):
                ae_day = self.rng.randint(min(3, follow_up_days), follow_up_days)
                term, min_g, max_g, rel = self.rng.choice(self.ae_terms)
                grade = self.rng.randint(min_g, max_g)
                if will_dropout and self.rng.random() < 0.4:
                    grade = min(4, grade + 1)
                is_serious = grade >= 3 or (grade == 2 and self.rng.random() < 0.15)
                action = "DOSE_REDUCED" if grade == 2 else ("DRUG_WITHDRAWN" if grade >= 3 else "NONE")
                outcome_status = "NOT_RECOVERED" if will_dropout and grade >= 3 else "RECOVERED"

                ae_onset = (start_date + timedelta(days=ae_day, hours=14)).isoformat()
                ae_resolved = None if outcome_status == "NOT_RECOVERED" else (start_date + timedelta(days=min(90, ae_day + self.rng.randint(2, 14)))).isoformat()

                all_adverse_events.append(
                    AdverseEventRecord(
                        id=str(uuid.UUID(int=self.rng.getrandbits(128), version=4)),
                        enrollment_id=e_id,
                        event_day=ae_day,
                        onset_at=ae_onset,
                        resolved_at=ae_resolved,
                        term=term,
                        ctcae_grade=grade,
                        is_serious=is_serious,
                        relatedness=rel,
                        action_taken=action,
                        outcome=outcome_status,
                        notes=f"Synthetic AE {term} (Grade {grade})",
                        created_at=now_iso,
                        updated_at=now_iso,
                    )
                )

            # Outcome Record
            outcome_rec = OutcomeRecord(
                id=str(uuid.UUID(int=self.rng.getrandbits(128), version=4)),
                enrollment_id=e_id,
                horizon_days=90,
                dropout_within_horizon=will_dropout,
                dropout_day=dropout_day,
                dropout_reason=dropout_reason,
                is_censored=False,
                censoring_day=None,
                follow_up_days=follow_up_days,
                completed_study=not will_dropout,
                notes="Generated 90-day synthetic outcome",
                created_at=now_iso,
                updated_at=now_iso,
            )
            all_outcomes.append(outcome_rec)

        return (
            participants,
            enrollments,
            all_dose_events,
            all_visit_events,
            all_measurements,
            all_adverse_events,
            all_outcomes,
        )

    def save_cohort_to_csv(
        self,
        output_dir: str,
        num_participants: int = 400,
    ) -> Dict[str, str]:
        """Generates cohort data and writes all 7 CSV files into output_dir."""
        os.makedirs(output_dir, exist_ok=True)

        (
            participants,
            enrollments,
            doses,
            visits,
            measurements,
            aes,
            outcomes,
        ) = self.generate_cohort(num_participants=num_participants)

        file_map = {
            "participants.csv": (participants, ParticipantRecord),
            "enrollments.csv": (enrollments, EnrollmentRecord),
            "dose_events.csv": (doses, DoseEventRecord),
            "visit_events.csv": (visits, VisitEventRecord),
            "measurements.csv": (measurements, MeasurementRecord),
            "adverse_events.csv": (aes, AdverseEventRecord),
            "outcomes.csv": (outcomes, OutcomeRecord),
        }

        saved_paths: Dict[str, str] = {}

        for filename, (data_list, dataclass_type) in file_map.items():
            filepath = os.path.join(output_dir, filename)
            if data_list:
                fields = list(asdict(data_list[0]).keys())
                with open(filepath, "w", newline="", encoding="utf-8") as f:
                    writer = csv.DictWriter(f, fieldnames=fields)
                    writer.writeheader()
                    for item in data_list:
                        writer.writerow(asdict(item))
            saved_paths[filename] = filepath

        return saved_paths


if __name__ == "__main__":
    generator = LongitudinalDataGenerator(seed=42)
    current_dir = os.path.dirname(os.path.abspath(__file__))
    saved = generator.save_cohort_to_csv(output_dir=current_dir, num_participants=400)
    print(f"Successfully generated dataset across {len(saved)} CSV files in {current_dir}")
