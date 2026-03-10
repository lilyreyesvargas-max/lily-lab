from odoo.tests.common import TransactionCase, tagged


@tagged('post_install', '-at_install')
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

    # ── Record-Level Security (multi-company isolation) ──────────────────────

    def _make_rls_context(self):
        """Create two companies, a doctor bound to S1, and base patients."""
        company_s1 = self.env['res.company'].create({'name': 'EHR Branch S1'})
        company_s2 = self.env['res.company'].create({'name': 'EHR Branch S2'})
        doctor_group = self.env.ref('clinic_core.clinic_group_doctor')
        doctor_s1 = self.env['res.users'].create({
            'name': 'EHR Doctor S1',
            'login': 'ehr_doc_s1@test.com',
            'company_id': company_s1.id,
            'company_ids': [(4, company_s1.id)],
            'groups_id': [(4, doctor_group.id)],
        })
        patient_s1 = self.env['clinic.patient'].sudo().create(
            {'name': 'EHR S1 Patient', 'company_id': company_s1.id}
        )
        patient_s2 = self.env['clinic.patient'].sudo().create(
            {'name': 'EHR S2 Patient', 'company_id': company_s2.id}
        )
        return company_s1, company_s2, doctor_s1, patient_s1, patient_s2

    def test_doctor_cannot_see_other_company_encounter(self):
        """RLS: doctor at S1 must not see encounters belonging to S2."""
        company_s1, company_s2, doctor_s1, patient_s1, patient_s2 = (
            self._make_rls_context()
        )
        self.env['clinic.ehr.encounter'].sudo().create({
            'patient_id': patient_s2.id, 'company_id': company_s2.id
        })
        visible = self.env['clinic.ehr.encounter'].with_user(doctor_s1).search([])
        for enc in visible:
            self.assertNotEqual(
                enc.company_id.id, company_s2.id,
                "Doctor S1 must not see encounters from Branch S2"
            )

    def test_doctor_can_see_own_company_encounter(self):
        """RLS: doctor at S1 must see encounters belonging to S1."""
        company_s1, company_s2, doctor_s1, patient_s1, patient_s2 = (
            self._make_rls_context()
        )
        enc_s1 = self.env['clinic.ehr.encounter'].sudo().create({
            'patient_id': patient_s1.id, 'company_id': company_s1.id
        })
        visible = self.env['clinic.ehr.encounter'].with_user(doctor_s1).search([])
        self.assertIn(enc_s1, visible, "Doctor S1 must see encounters from Branch S1")

    # ── Specialty Extension Tests ─────────────────────────────────────────────

    def test_base_encounter_has_no_specialty_fields(self):
        """Base encounter must not have specialty-specific fields after refactor."""
        encounter_fields = self.env['clinic.ehr.encounter']._fields
        self.assertNotIn('lmp', encounter_fields)
        self.assertNotIn('od_sphere', encounter_fields)
        self.assertNotIn('tooth_chart', encounter_fields)

    def test_gynecology_extension_creation(self):
        encounter = self.env['clinic.ehr.encounter'].create({
            'patient_id': self.patient.id,
        })
        gyn = self.env['clinic.ehr.encounter.gynecology'].create({
            'encounter_id': encounter.id,
            'gravida': 2,
            'para': 1,
            'abortus': 0,
        })
        self.assertEqual(gyn.encounter_id, encounter)
        self.assertGreaterEqual(gyn.gestational_age, 0)

    def test_ophthalmology_extension_creation(self):
        encounter = self.env['clinic.ehr.encounter'].create({
            'patient_id': self.patient.id,
        })
        oph = self.env['clinic.ehr.encounter.ophthalmology'].create({
            'encounter_id': encounter.id,
            'od_sphere': -2.0,
            'od_va': '20/40',
            'iop_od': 14.0,
        })
        self.assertEqual(oph.encounter_id, encounter)

    def test_stomatology_extension_creation(self):
        encounter = self.env['clinic.ehr.encounter'].create({
            'patient_id': self.patient.id,
        })
        stoma = self.env['clinic.ehr.encounter.stomatology'].create({
            'encounter_id': encounter.id,
            'dental_procedure': 'Extraction #18',
        })
        self.assertEqual(stoma.encounter_id, encounter)
