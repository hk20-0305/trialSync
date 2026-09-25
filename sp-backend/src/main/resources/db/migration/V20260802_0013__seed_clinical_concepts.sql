-- V20260802_0013__seed_clinical_concepts.sql
-- Seed the 25 standard clinical catalog concepts.

INSERT INTO clinical_concepts (
    id, key, fact_type, concept, display_label, concept_group, input_kind,
    allowed_assertions_json, fixed_unit, effective_date_required,
    screening_supported, help_text, display_order, active, created_at, updated_at
) VALUES
-- Conditions
(gen_random_uuid(), 'type1_diabetes', 'condition', 'type1_diabetes', 'Type 1 diabetes', 'conditions', 'status',
 '["present", "absent", "unknown"]', NULL, FALSE, TRUE, 'Record whether Type 1 diabetes is present, absent, or unknown.', 10, TRUE, now(), now()),
(gen_random_uuid(), 'type2_diabetes', 'condition', 'type2_diabetes', 'Type 2 diabetes', 'conditions', 'status',
 '["present", "absent", "unknown"]', NULL, FALSE, TRUE, 'Record whether Type 2 diabetes is present, absent, or unknown.', 20, TRUE, now(), now()),
(gen_random_uuid(), 'hypertension', 'condition', 'hypertension', 'Hypertension', 'conditions', 'status',
 '["present", "absent", "unknown"]', NULL, FALSE, TRUE, 'Record whether hypertension is present, absent, or unknown.', 30, TRUE, now(), now()),
(gen_random_uuid(), 'asthma', 'condition', 'asthma', 'Asthma', 'conditions', 'status',
 '["present", "absent", "unknown"]', NULL, FALSE, TRUE, 'Record whether asthma is present, absent, or unknown.', 40, TRUE, now(), now()),
(gen_random_uuid(), 'pregnancy', 'condition', 'pregnancy', 'Pregnancy status', 'conditions', 'pregnancy_status',
 '["present", "absent", "unknown"]', NULL, TRUE, TRUE, 'Record the assessed pregnancy status and assessment date.', 50, TRUE, now(), now()),

-- Medications
(gen_random_uuid(), 'metformin', 'medication', 'metformin', 'Metformin', 'medications', 'status',
 '["present", "absent", "unknown"]', NULL, FALSE, TRUE, 'Record whether metformin use is present, absent, or unknown.', 10, TRUE, now(), now()),
(gen_random_uuid(), 'atorvastatin', 'medication', 'atorvastatin', 'Atorvastatin', 'medications', 'status',
 '["present", "absent", "unknown"]', NULL, FALSE, TRUE, 'Record whether atorvastatin use is present, absent, or unknown.', 20, TRUE, now(), now()),
(gen_random_uuid(), 'insulin', 'medication', 'insulin', 'Insulin', 'medications', 'status',
 '["present", "absent", "unknown"]', NULL, FALSE, TRUE, 'Record whether insulin use is present, absent, or unknown.', 30, TRUE, now(), now()),
(gen_random_uuid(), 'semaglutide', 'medication', 'semaglutide', 'Semaglutide', 'medications', 'status',
 '["present", "absent", "unknown"]', NULL, FALSE, TRUE, 'Record whether semaglutide use is present, absent, or unknown.', 40, TRUE, now(), now()),

-- Observations / Labs / Vitals
(gen_random_uuid(), 'hba1c', 'observation', 'hba1c', 'HbA1c', 'observations', 'numeric',
 '["present", "unknown"]', '%', FALSE, TRUE, 'Record the measured HbA1c result.', 10, TRUE, now(), now()),
(gen_random_uuid(), 'fasting_glucose', 'observation', 'fasting_glucose', 'Fasting glucose', 'observations', 'numeric',
 '["present", "unknown"]', 'mg/dL', FALSE, TRUE, 'Record the measured fasting glucose result.', 20, TRUE, now(), now()),
(gen_random_uuid(), 'egfr', 'observation', 'egfr', 'eGFR', 'observations', 'numeric',
 '["present", "unknown"]', 'mL/min/1.73m2', FALSE, TRUE, 'Record the measured estimated filtration rate.', 30, TRUE, now(), now()),
(gen_random_uuid(), 'creatinine', 'observation', 'creatinine', 'Creatinine', 'observations', 'numeric',
 '["present", "unknown"]', 'mg/dL', FALSE, TRUE, 'Record the measured creatinine result.', 40, TRUE, now(), now()),
(gen_random_uuid(), 'alt', 'observation', 'alt', 'ALT', 'observations', 'numeric',
 '["present", "unknown"]', 'U/L', FALSE, TRUE, 'Record the measured alanine transaminase result.', 50, TRUE, now(), now()),
(gen_random_uuid(), 'ast', 'observation', 'ast', 'AST', 'observations', 'numeric',
 '["present", "unknown"]', 'U/L', FALSE, TRUE, 'Record the measured aspartate transaminase result.', 60, TRUE, now(), now()),
(gen_random_uuid(), 'hemoglobin', 'observation', 'hemoglobin', 'Hemoglobin', 'observations', 'numeric',
 '["present", "unknown"]', 'g/dL', FALSE, TRUE, 'Record the measured hemoglobin result.', 70, TRUE, now(), now()),
(gen_random_uuid(), 'wbc', 'observation', 'wbc', 'White blood cell count', 'observations', 'numeric',
 '["present", "unknown"]', '10^9/L', FALSE, TRUE, 'Record the measured white blood cell count.', 80, TRUE, now(), now()),
(gen_random_uuid(), 'platelets', 'observation', 'platelets', 'Platelets', 'observations', 'numeric',
 '["present", "unknown"]', '10^9/L', FALSE, TRUE, 'Record the measured platelet count.', 90, TRUE, now(), now()),
(gen_random_uuid(), 'ldl', 'observation', 'ldl', 'LDL cholesterol', 'observations', 'numeric',
 '["present", "unknown"]', 'mg/dL', FALSE, TRUE, 'Record the measured LDL result.', 100, TRUE, now(), now()),
(gen_random_uuid(), 'triglycerides', 'observation', 'triglycerides', 'Triglycerides', 'observations', 'numeric',
 '["present", "unknown"]', 'mg/dL', FALSE, TRUE, 'Record the measured triglyceride result.', 110, TRUE, now(), now()),
(gen_random_uuid(), 'bmi', 'observation', 'bmi', 'BMI', 'observations', 'numeric',
 '["present", "unknown"]', 'kg/m2', FALSE, TRUE, 'Record the measured body mass index.', 120, TRUE, now(), now()),
(gen_random_uuid(), 'systolic_bp', 'observation', 'systolic_bp', 'Systolic blood pressure', 'observations', 'numeric',
 '["present", "unknown"]', 'mmHg', FALSE, TRUE, 'Record the measured systolic blood pressure.', 130, TRUE, now(), now()),
(gen_random_uuid(), 'diastolic_bp', 'observation', 'diastolic_bp', 'Diastolic blood pressure', 'observations', 'numeric',
 '["present", "unknown"]', 'mmHg', FALSE, TRUE, 'Record the measured diastolic blood pressure.', 140, TRUE, now(), now()),
(gen_random_uuid(), 'potassium', 'observation', 'potassium', 'Potassium', 'observations', 'numeric',
 '["present", "unknown"]', 'mmol/L', FALSE, TRUE, 'Record the measured potassium result.', 150, TRUE, now(), now()),
(gen_random_uuid(), 'albumin', 'observation', 'albumin', 'Albumin', 'observations', 'numeric',
 '["present", "unknown"]', 'g/dL', FALSE, TRUE, 'Record the measured albumin result.', 160, TRUE, now(), now())
ON CONFLICT (key) DO NOTHING;
