from odoo import fields, models


class ClinicSpecialty(models.Model):
    _name = 'clinic.specialty'
    _description = 'Clinical Specialty'
    _order = 'sequence, name'

    name = fields.Char(string='Specialty', required=True)
    code = fields.Char(string='Code')
    description = fields.Text(string='Description')
    sequence = fields.Integer(default=10)
    active = fields.Boolean(default=True)
    color = fields.Integer(string='Color Index', default=0)
