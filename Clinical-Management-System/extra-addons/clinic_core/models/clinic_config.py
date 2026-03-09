from odoo import fields, models


class ClinicConfig(models.Model):
    _name = 'clinic.config'
    _description = 'Clinic Configuration'
    _inherits = {'res.company': 'company_id'}

    company_id = fields.Many2one('res.company', string='Company', required=True, ondelete='cascade')
    clinic_code = fields.Char(string='Clinic Code')
    specialty_ids = fields.Many2many('clinic.specialty', string='Specialties')
    notes = fields.Text(string='Notes')
