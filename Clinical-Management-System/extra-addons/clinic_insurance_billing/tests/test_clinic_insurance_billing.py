from odoo.tests.common import TransactionCase, tagged


@tagged('post_install', '-at_install')
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

    # ── Record-Level Security (multi-company isolation) ──────────────────────

    def _make_rls_context(self):
        company_s1 = self.env['res.company'].create({'name': 'Billing Branch S1'})
        company_s2 = self.env['res.company'].create({'name': 'Billing Branch S2'})
        billing_group = self.env.ref('clinic_core.clinic_group_billing')
        billing_user_s1 = self.env['res.users'].create({
            'name': 'Billing User S1',
            'login': 'billing_s1@test.com',
            'company_id': company_s1.id,
            'company_ids': [(4, company_s1.id)],
            'groups_id': [(4, billing_group.id)],
        })
        patient_s1 = self.env['clinic.patient'].sudo().create(
            {'name': 'Billing S1 Patient', 'company_id': company_s1.id}
        )
        patient_s2 = self.env['clinic.patient'].sudo().create(
            {'name': 'Billing S2 Patient', 'company_id': company_s2.id}
        )
        policy_s2 = self.env['clinic.patient.policy'].sudo().create({
            'patient_id': patient_s2.id,
            'insurer_id': self.insurer.id,
            'policy_number': 'S2-001',
        })
        return company_s1, company_s2, billing_user_s1, patient_s1, patient_s2, policy_s2

    def test_billing_user_cannot_see_other_company_claim(self):
        """RLS: billing user at S1 must not see claims from S2."""
        company_s1, company_s2, billing_user_s1, patient_s1, patient_s2, policy_s2 = (
            self._make_rls_context()
        )
        self.env['clinic.billing.claim'].sudo().create({
            'patient_id': patient_s2.id,
            'policy_id': policy_s2.id,
            'service_date': '2025-01-01',
            'company_id': company_s2.id,
        })
        visible = self.env['clinic.billing.claim'].with_user(billing_user_s1).search([])
        for claim in visible:
            self.assertNotEqual(
                claim.company_id.id, company_s2.id,
                "Billing user S1 must not see claims from Branch S2"
            )

    def test_billing_user_can_see_own_company_claim(self):
        """RLS: billing user at S1 must see claims from S1."""
        company_s1, company_s2, billing_user_s1, patient_s1, patient_s2, policy_s2 = (
            self._make_rls_context()
        )
        policy_s1 = self.env['clinic.patient.policy'].sudo().create({
            'patient_id': patient_s1.id,
            'insurer_id': self.insurer.id,
            'policy_number': 'S1-001',
        })
        claim_s1 = self.env['clinic.billing.claim'].sudo().create({
            'patient_id': patient_s1.id,
            'policy_id': policy_s1.id,
            'service_date': '2025-01-01',
            'company_id': company_s1.id,
        })
        visible = self.env['clinic.billing.claim'].with_user(billing_user_s1).search([])
        self.assertIn(claim_s1, visible, "Billing user S1 must see claims from Branch S1")
