from odoo.tests.common import TransactionCase


class TestClinicBilling(TransactionCase):

    def setUp(self):
        super().setUp()
        self.insurer = self.env['clinic.insurer'].create({'name': 'Test Ins', 'code': 'T1'})
        self.patient = self.env['clinic.patient'].create({'name': 'Billing Patient'})
        self.policy = self.env['clinic.patient.policy'].create({
            'patient_id': self.patient.id, 'insurer_id': self.insurer.id,
            'policy_number': 'TEST-001'
        })

    def test_claim_sequence(self):
        claim = self.env['clinic.billing.claim'].create({
            'patient_id': self.patient.id, 'policy_id': self.policy.id,
            'service_date': '2024-01-01',
        })
        self.assertIn('CLAIM', claim.name)

    def test_split_billing_80_20(self):
        claim = self.env['clinic.billing.claim'].create({
            'patient_id': self.patient.id, 'policy_id': self.policy.id,
            'service_date': '2024-01-01',
        })
        self.env['clinic.billing.claim.line'].create({
            'claim_id': claim.id, 'service_code': '99213',
            'quantity': 1.0, 'unit_price': 150.0, 'coverage_pct': 80.0,
        })
        self.assertAlmostEqual(claim.total_amount, 150.0)
        self.assertAlmostEqual(claim.insurer_amount, 120.0)
        self.assertAlmostEqual(claim.patient_amount, 30.0)

    def test_claim_state_transitions(self):
        claim = self.env['clinic.billing.claim'].create({
            'patient_id': self.patient.id, 'policy_id': self.policy.id,
            'service_date': '2024-01-01',
        })
        self.assertEqual(claim.state, 'draft')
        claim.action_submit()
        self.assertEqual(claim.state, 'submitted')
        claim.action_mark_paid()
        self.assertEqual(claim.state, 'paid')
