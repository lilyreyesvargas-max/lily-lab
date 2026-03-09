from odoo import api, fields, models


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
