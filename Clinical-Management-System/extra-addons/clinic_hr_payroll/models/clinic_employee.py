from odoo import api, fields, models
from odoo.exceptions import ValidationError


class ClinicEmployee(models.Model):
    _inherit = 'hr.employee'

    clinic_role = fields.Selection([
        ('doctor', 'Doctor'), ('nurse', 'Nurse'),
        ('receptionist', 'Receptionist'), ('admin', 'Admin'),
        ('technician', 'Technician'), ('other', 'Other'),
    ], string='Clinic Role')
    specialty_id = fields.Many2one('clinic.specialty', string='Medical Specialty')
    license_number = fields.Char(string='Medical License No.')
    license_expiry = fields.Date(string='License Expiry')
    is_physician = fields.Boolean(string='Is Physician', compute='_compute_is_physician', store=True)

    @api.depends('clinic_role')
    def _compute_is_physician(self):
        for rec in self:
            rec.is_physician = rec.clinic_role == 'doctor'

    @api.constrains('clinic_role', 'user_id', 'barcode', 'work_email', 'job_id')
    def _check_clinic_staff_required_fields(self):
        """Enforce required fields for clinical staff members.

        When clinic_role is set, the following fields are mandatory:
        - user_id: linked Odoo user (needed for system login)
        - barcode: Badge ID (needed for access control)
        - work_email: work e-mail (needed for notifications)
        - job_id: job position (needed for HR classification)
        """
        for rec in self:
            if not rec.clinic_role:
                continue

            missing = []
            if not rec.user_id:
                missing.append('User (user_id)')
            if not rec.barcode:
                missing.append('Badge ID (barcode)')
            if not rec.work_email:
                missing.append('Work Email (work_email)')
            if not rec.job_id:
                missing.append('Job Position (job_id)')

            if missing:
                raise ValidationError(
                    "Clinical staff requires the following fields to be filled in: %s"
                    % ', '.join(missing)
                )
