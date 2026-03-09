from odoo.tests.common import TransactionCase


class TestClinicCore(TransactionCase):

    def test_icd10_name_get(self):
        icd = self.env['clinic.icd10'].create({'code': 'TEST01', 'description': 'Test Diagnosis'})
        self.assertEqual(icd.display_name, 'TEST01 — Test Diagnosis')

    def test_specialty_creation(self):
        spec = self.env['clinic.specialty'].create({'name': 'Cardiology', 'code': 'CARD'})
        self.assertEqual(spec.name, 'Cardiology')
        self.assertTrue(spec.active)

    def test_icd10_uniqueness(self):
        self.env['clinic.icd10'].create({'code': 'UNIQUE01', 'description': 'First'})
        with self.assertRaises(Exception):
            self.env['clinic.icd10'].create({'code': 'UNIQUE01', 'description': 'Duplicate'})
