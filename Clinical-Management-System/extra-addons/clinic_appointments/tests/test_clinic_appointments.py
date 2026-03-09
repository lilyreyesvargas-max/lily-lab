from odoo.tests.common import TransactionCase


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
