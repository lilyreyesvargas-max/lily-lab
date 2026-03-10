from odoo.tests.common import TransactionCase
from odoo.exceptions import ValidationError


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

        # Create a job position used by clinic staff tests
        self.job = self.env['hr.job'].search([('name', '=', 'Test Doctor')], limit=1)
        if not self.job:
            self.job = self.env['hr.job'].create({'name': 'Test Doctor'})

        # Create a linked user for clinic staff tests
        self.user = self.env['res.users'].search([('login', '=', 'test.clinic.user@clinic.local')], limit=1)
        if not self.user:
            self.user = self.env['res.users'].create({
                'name': 'Test Clinic User',
                'login': 'test.clinic.user@clinic.local',
                'email': 'test.clinic.user@clinic.local',
                'groups_id': [(4, self.env.ref('base.group_user').id)],
            })

    def _clinic_employee_vals(self, **overrides):
        """Return a minimal valid set of vals for a clinic employee."""
        vals = {
            'name': 'Dr. Test',
            'clinic_role': 'doctor',
            'user_id': self.user.id,
            'barcode': 'BADGE-TEST-001',
            'work_email': 'dr.test@clinic.local',
            'job_id': self.job.id,
        }
        vals.update(overrides)
        return vals

    # ── existing behaviour tests (updated to supply required fields) ──────────

    def test_employee_clinic_role(self):
        emp = self.Employee.create(self._clinic_employee_vals(name='Dr. Role Test'))
        self.assertEqual(emp.clinic_role, 'doctor')
        self.assertTrue(emp.is_physician)

    def test_is_physician_false_for_nurse(self):
        emp = self.Employee.create(self._clinic_employee_vals(
            name='Nurse Test',
            clinic_role='nurse',
        ))
        self.assertFalse(emp.is_physician)

    def test_employee_with_specialty(self):
        emp = self.Employee.create(self._clinic_employee_vals(
            name='Dr. Specialist',
            specialty_id=self.specialty.id,
        ))
        self.assertEqual(emp.specialty_id, self.specialty)

    def test_employee_no_role(self):
        emp = self.Employee.create({'name': 'Generic Employee'})
        self.assertFalse(emp.clinic_role)
        self.assertFalse(emp.is_physician)

    # ── RED: new validation tests ─────────────────────────────────────────────

    def test_clinic_employee_requires_user(self):
        """Clinic employee without user_id must raise ValidationError."""
        with self.assertRaises(ValidationError):
            self.Employee.create(self._clinic_employee_vals(user_id=False))

    def test_clinic_employee_requires_badge(self):
        """Clinic employee without barcode must raise ValidationError."""
        with self.assertRaises(ValidationError):
            self.Employee.create(self._clinic_employee_vals(barcode=False))

    def test_clinic_employee_requires_email(self):
        """Clinic employee without work_email must raise ValidationError."""
        with self.assertRaises(ValidationError):
            self.Employee.create(self._clinic_employee_vals(work_email=False))

    def test_clinic_employee_requires_job(self):
        """Clinic employee without job_id must raise ValidationError."""
        with self.assertRaises(ValidationError):
            self.Employee.create(self._clinic_employee_vals(job_id=False))

    def test_non_clinic_employee_no_constraint(self):
        """Non-clinic employees must save without user/badge/email/job."""
        emp = self.Employee.create({'name': 'Plain Staff Member'})
        self.assertFalse(emp.clinic_role)

    def test_clinic_employee_all_required_fields_passes(self):
        """Clinic employee with all required fields must save without error."""
        emp = self.Employee.create(self._clinic_employee_vals(name='Dr. Complete'))
        self.assertEqual(emp.clinic_role, 'doctor')
        self.assertTrue(emp.user_id)
        self.assertTrue(emp.barcode)
        self.assertTrue(emp.work_email)
        self.assertTrue(emp.job_id)

    def test_validation_error_message_lists_missing_fields(self):
        """ValidationError message must name the missing field(s)."""
        try:
            self.Employee.create(self._clinic_employee_vals(user_id=False, barcode=False))
            self.fail("Expected ValidationError was not raised")
        except ValidationError as e:
            msg = str(e)
            self.assertIn('user_id', msg.lower() + msg)
            self.assertIn('barcode', msg.lower() + msg)

    def test_write_clinic_role_triggers_constraint(self):
        """Setting clinic_role via write on an incomplete employee must raise ValidationError."""
        emp = self.Employee.create({'name': 'Staff To Upgrade'})
        with self.assertRaises(ValidationError):
            emp.write({'clinic_role': 'receptionist'})

    def test_write_clinic_role_with_all_fields_passes(self):
        """Setting clinic_role via write with all required fields must succeed."""
        emp = self.Employee.create({'name': 'Staff To Upgrade OK'})
        emp.write({
            'clinic_role': 'receptionist',
            'user_id': self.user.id,
            'barcode': 'BADGE-WRITE-001',
            'work_email': 'staff.upgrade@clinic.local',
            'job_id': self.job.id,
        })
        self.assertEqual(emp.clinic_role, 'receptionist')
