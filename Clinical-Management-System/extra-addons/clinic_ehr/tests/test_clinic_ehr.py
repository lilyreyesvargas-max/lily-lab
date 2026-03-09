from odoo.tests.common import TransactionCase


class TestClinicEhr(TransactionCase):

    def setUp(self):
        super().setUp()
        self.patient = self.env['clinic.patient'].create({'name': 'EHR Test Patient'})

    def test_encounter_ref_auto(self):
        enc = self.env['clinic.ehr.encounter'].create({'patient_id': self.patient.id})
        self.assertIn('ENCTR', enc.ref)

    def test_encounter_state_transitions(self):
        enc = self.env['clinic.ehr.encounter'].create({'patient_id': self.patient.id})
        self.assertEqual(enc.state, 'draft')
        enc.action_start()
        self.assertEqual(enc.state, 'in_progress')
        enc.action_complete()
        self.assertEqual(enc.state, 'completed')

    def test_bmi_computation(self):
        enc = self.env['clinic.ehr.encounter'].create({
            'patient_id': self.patient.id, 'weight': 70.0, 'height': 175.0
        })
        self.assertAlmostEqual(enc.bmi, 22.9, places=0)

    def test_diagnosis_creation(self):
        icd = self.env['clinic.icd10'].create({'code': 'TEST01', 'description': 'Test'})
        enc = self.env['clinic.ehr.encounter'].create({'patient_id': self.patient.id})
        diag = self.env['clinic.ehr.diagnosis'].create({
            'encounter_id': enc.id, 'icd10_id': icd.id, 'diagnosis_type': 'primary'
        })
        self.assertIn(diag, enc.diagnosis_ids)
