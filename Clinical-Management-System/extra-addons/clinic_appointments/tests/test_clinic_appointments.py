from odoo.tests.common import TransactionCase, tagged
from odoo.exceptions import ValidationError


@tagged('post_install', '-at_install')
class TestClinicAppointments(TransactionCase):

    def setUp(self):
        super().setUp()
        self.patient = self.env['clinic.patient'].create({'name': 'Appt Patient'})

    def test_appointment_sequence(self):
        appt = self.env['clinic.appointment'].create({
            'patient_id': self.patient.id,
            'physician_id': self.env.uid,
            'appointment_date': '2024-06-01 10:00:00',
        })
        self.assertIn('APPT', appt.name)

    def test_state_workflow(self):
        appt = self.env['clinic.appointment'].create({
            'patient_id': self.patient.id,
            'physician_id': self.env.uid,
            'appointment_date': '2024-06-01 11:00:00',
        })
        self.assertEqual(appt.state, 'scheduled')
        appt.action_confirm()
        self.assertEqual(appt.state, 'confirmed')
        appt.action_arrived()
        self.assertEqual(appt.state, 'arrived')
        appt.action_start()
        self.assertEqual(appt.state, 'in_consultation')
        appt.action_complete()
        self.assertEqual(appt.state, 'completed')

    def test_date_end_computed(self):
        appt = self.env['clinic.appointment'].create({
            'patient_id': self.patient.id,
            'physician_id': self.env.uid,
            'appointment_date': '2024-06-01 10:00:00',
            'duration': 1.0,
        })
        self.assertIsNotNone(appt.date_end)
        # date_end should be 1 hour after start
        from datetime import datetime, timedelta
        expected = datetime(2024, 6, 1, 11, 0, 0)
        self.assertEqual(appt.date_end, expected)

    # ── Record-Level Security (multi-company isolation) ──────────────────────

    def _make_rls_context(self):
        company_s1 = self.env['res.company'].create({'name': 'Appt Branch S1'})
        company_s2 = self.env['res.company'].create({'name': 'Appt Branch S2'})
        doctor_group = self.env.ref('clinic_core.clinic_group_doctor')
        doctor_s1 = self.env['res.users'].create({
            'name': 'Appt Doctor S1',
            'login': 'appt_doc_s1@test.com',
            'company_id': company_s1.id,
            'company_ids': [(4, company_s1.id)],
            'groups_id': [(4, doctor_group.id)],
        })
        patient_s1 = self.env['clinic.patient'].sudo().create(
            {'name': 'Appt S1 Patient', 'company_id': company_s1.id}
        )
        patient_s2 = self.env['clinic.patient'].sudo().create(
            {'name': 'Appt S2 Patient', 'company_id': company_s2.id}
        )
        return company_s1, company_s2, doctor_s1, patient_s1, patient_s2

    def test_doctor_cannot_see_other_company_appointment(self):
        """RLS: doctor at S1 must not see appointments from S2."""
        company_s1, company_s2, doctor_s1, patient_s1, patient_s2 = (
            self._make_rls_context()
        )
        self.env['clinic.appointment'].sudo().create({
            'patient_id': patient_s2.id,
            'physician_id': doctor_s1.id,
            'appointment_date': '2025-01-10 09:00:00',
            'company_id': company_s2.id,
        })
        visible = self.env['clinic.appointment'].with_user(doctor_s1).search([])
        for appt in visible:
            self.assertNotEqual(
                appt.company_id.id, company_s2.id,
                "Doctor S1 must not see appointments from Branch S2"
            )

    def test_doctor_can_see_own_company_appointment(self):
        """RLS: doctor at S1 must see appointments from S1."""
        company_s1, company_s2, doctor_s1, patient_s1, patient_s2 = (
            self._make_rls_context()
        )
        appt_s1 = self.env['clinic.appointment'].sudo().create({
            'patient_id': patient_s1.id,
            'physician_id': doctor_s1.id,
            'appointment_date': '2025-01-10 10:00:00',
            'company_id': company_s1.id,
        })
        visible = self.env['clinic.appointment'].with_user(doctor_s1).search([])
        self.assertIn(appt_s1, visible, "Doctor S1 must see appointments from Branch S1")

    # ── Double-booking constraint ─────────────────────────────────────────────

    def test_physician_overlap_raises_validation_error(self):
        """Two overlapping appointments for the same physician must be rejected."""
        appt1 = self.env['clinic.appointment'].create({
            'patient_id': self.patient.id,
            'physician_id': self.env.uid,
            'appointment_date': '2025-03-01 10:00:00',
            'duration': 1.0,
        })
        with self.assertRaises(ValidationError):
            self.env['clinic.appointment'].create({
                'patient_id': self.patient.id,
                'physician_id': self.env.uid,
                'appointment_date': '2025-03-01 10:30:00',
                'duration': 1.0,
            })

    def test_physician_no_overlap_adjacent_slots(self):
        """Adjacent (non-overlapping) slots for same physician must be allowed."""
        self.env['clinic.appointment'].create({
            'patient_id': self.patient.id,
            'physician_id': self.env.uid,
            'appointment_date': '2025-03-01 09:00:00',
            'duration': 0.5,
        })
        # Starts exactly when the first ends — no overlap
        appt2 = self.env['clinic.appointment'].create({
            'patient_id': self.patient.id,
            'physician_id': self.env.uid,
            'appointment_date': '2025-03-01 09:30:00',
            'duration': 0.5,
        })
        self.assertTrue(appt2.id, "Adjacent appointments must be created without error")

    def test_cancelled_appointment_does_not_block_slot(self):
        """A cancelled appointment must not prevent a new booking in the same slot."""
        appt1 = self.env['clinic.appointment'].create({
            'patient_id': self.patient.id,
            'physician_id': self.env.uid,
            'appointment_date': '2025-03-02 10:00:00',
            'duration': 1.0,
        })
        appt1.action_cancel()
        # Same slot should be bookable again
        appt2 = self.env['clinic.appointment'].create({
            'patient_id': self.patient.id,
            'physician_id': self.env.uid,
            'appointment_date': '2025-03-02 10:00:00',
            'duration': 1.0,
        })
        self.assertTrue(appt2.id)
