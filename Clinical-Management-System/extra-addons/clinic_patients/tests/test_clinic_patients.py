from odoo.tests.common import TransactionCase


class TestClinicPatients(TransactionCase):

    def test_patient_ref_auto_generated(self):
        patient = self.env['clinic.patient'].create({'name': 'Test Patient'})
        self.assertNotEqual(patient.ref, 'New')
        self.assertIn('PTNT', patient.ref)

    def test_patient_age_computed(self):
        from datetime import date
        patient = self.env['clinic.patient'].create({
            'name': 'Age Test', 'date_of_birth': '1990-01-01'
        })
        self.assertGreater(patient.age, 0)

    def test_insurer_plan_creation(self):
        insurer = self.env['clinic.insurer'].create({
            'name': 'Test Insurer', 'code': 'TEST', 'payer_id': 'TEST999'
        })
        plan = self.env['clinic.insurer.plan'].create({
            'insurer_id': insurer.id,
            'name': 'Test PPO Plan',
            'coverage_type': 'ppo',
            'deductible': 1000.0,
        })
        self.assertEqual(plan.insurer_id, insurer)

    def test_patient_policy_assignment(self):
        insurer = self.env['clinic.insurer'].create({'name': 'Ins2', 'code': 'INS2'})
        patient = self.env['clinic.patient'].create({'name': 'Pol Patient'})
        policy = self.env['clinic.patient.policy'].create({
            'patient_id': patient.id,
            'insurer_id': insurer.id,
            'policy_number': 'POL-001',
        })
        self.assertIn(policy, patient.policy_ids)
