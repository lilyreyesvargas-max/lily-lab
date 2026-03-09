from odoo.tests.common import TransactionCase


class TestClinicShift(TransactionCase):

    def setUp(self):
        super().setUp()
        self.ShiftDay = self.env['clinic.shift.day']
        self.Shift = self.env['clinic.shift']
        self.day_mon = self.ShiftDay.search([('code', '=', 'MON')], limit=1)
        self.day_fri = self.ShiftDay.search([('code', '=', 'FRI')], limit=1)

    def test_shift_day_data_loaded(self):
        days = self.ShiftDay.search([])
        self.assertEqual(len(days), 7, "All 7 days should be loaded")
        codes = days.mapped('code')
        self.assertIn('MON', codes)
        self.assertIn('SUN', codes)

    def test_shift_duration_computed(self):
        shift = self.Shift.create({
            'name': 'Test Morning',
            'hour_from': 7.0,
            'hour_to': 15.0,
        })
        self.assertAlmostEqual(shift.duration, 8.0)

    def test_shift_duration_overnight(self):
        shift = self.Shift.create({
            'name': 'Night Shift',
            'hour_from': 22.0,
            'hour_to': 6.0,
        })
        self.assertAlmostEqual(shift.duration, 8.0)

    def test_shift_with_days(self):
        shift = self.Shift.create({
            'name': 'Weekday Shift',
            'hour_from': 8.0,
            'hour_to': 16.0,
            'day_ids': [(6, 0, [self.day_mon.id, self.day_fri.id])],
        })
        self.assertEqual(len(shift.day_ids), 2)

    def test_shift_active_default(self):
        shift = self.Shift.create({
            'name': 'Active Shift',
            'hour_from': 9.0,
            'hour_to': 17.0,
        })
        self.assertTrue(shift.active)


class TestClinicEmployee(TransactionCase):

    def setUp(self):
        super().setUp()
        self.Employee = self.env['hr.employee']
        self.Specialty = self.env['clinic.specialty']
        self.specialty = self.Specialty.search([('code', '=', 'MG')], limit=1)
        if not self.specialty:
            self.specialty = self.Specialty.create({'name': 'General Medicine', 'code': 'MG'})

    def test_employee_clinic_role(self):
        emp = self.Employee.create({
            'name': 'Dr. Test',
            'clinic_role': 'doctor',
        })
        self.assertEqual(emp.clinic_role, 'doctor')
        self.assertTrue(emp.is_physician)

    def test_is_physician_false_for_nurse(self):
        emp = self.Employee.create({
            'name': 'Nurse Test',
            'clinic_role': 'nurse',
        })
        self.assertFalse(emp.is_physician)

    def test_employee_with_specialty(self):
        emp = self.Employee.create({
            'name': 'Dr. Specialist',
            'clinic_role': 'doctor',
            'specialty_id': self.specialty.id,
        })
        self.assertEqual(emp.specialty_id, self.specialty)

    def test_employee_no_role(self):
        emp = self.Employee.create({'name': 'Generic Employee'})
        self.assertFalse(emp.clinic_role)
        self.assertFalse(emp.is_physician)
