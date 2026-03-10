from odoo.tests.common import TransactionCase, tagged


@tagged('post_install', '-at_install')
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

    # ── Record-Level Security (multi-company isolation) ──────────────────────

    def _make_rls_users(self):
        """Helper: create two companies and one doctor per company."""
        company_s1 = self.env['res.company'].create({'name': 'Test Branch S1'})
        company_s2 = self.env['res.company'].create({'name': 'Test Branch S2'})
        doctor_group = self.env.ref('clinic_core.clinic_group_doctor')
        doctor_s1 = self.env['res.users'].create({
            'name': 'Doctor S1',
            'login': 'doc_s1_rls@test.com',
            'company_id': company_s1.id,
            'company_ids': [(4, company_s1.id)],
            'groups_id': [(4, doctor_group.id)],
        })
        return company_s1, company_s2, doctor_s1

    def test_doctor_cannot_see_other_company_patient(self):
        """RLS: doctor at S1 must not see patients belonging to S2."""
        company_s1, company_s2, doctor_s1 = self._make_rls_users()
        self.env['clinic.patient'].sudo().create({
            'name': 'S2 Patient RLS', 'company_id': company_s2.id
        })
        visible = self.env['clinic.patient'].with_user(doctor_s1).search([])
        names = visible.mapped('name')
        self.assertNotIn(
            'S2 Patient RLS', names,
            "Doctor S1 must not see patients from Branch S2"
        )

    def test_doctor_can_see_own_company_patient(self):
        """RLS: doctor at S1 must see patients belonging to S1."""
        company_s1, company_s2, doctor_s1 = self._make_rls_users()
        self.env['clinic.patient'].sudo().create({
            'name': 'S1 Patient RLS', 'company_id': company_s1.id
        })
        visible = self.env['clinic.patient'].with_user(doctor_s1).search([])
        names = visible.mapped('name')
        self.assertIn(
            'S1 Patient RLS', names,
            "Doctor S1 must see patients from Branch S1"
        )

    def test_admin_sees_all_companies_patients(self):
        """RLS: clinic admin must see patients from all branches."""
        company_s1, company_s2, _doctor = self._make_rls_users()
        admin_group = self.env.ref('clinic_core.clinic_group_admin')
        admin_user = self.env['res.users'].create({
            'name': 'Clinic Admin RLS',
            'login': 'clinic_admin_rls@test.com',
            'company_id': company_s1.id,
            'company_ids': [(4, company_s1.id)],
            'groups_id': [(4, admin_group.id)],
        })
        self.env['clinic.patient'].sudo().create(
            {'name': 'S1 Admin Patient', 'company_id': company_s1.id}
        )
        self.env['clinic.patient'].sudo().create(
            {'name': 'S2 Admin Patient', 'company_id': company_s2.id}
        )
        visible = self.env['clinic.patient'].with_user(admin_user).search([])
        names = visible.mapped('name')
        self.assertIn('S1 Admin Patient', names)
        self.assertIn('S2 Admin Patient', names)
