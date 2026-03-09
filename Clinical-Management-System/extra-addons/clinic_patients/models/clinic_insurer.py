from odoo import fields, models


class ClinicInsurer(models.Model):
    _name = 'clinic.insurer'
    _description = 'Health Insurer'
    _order = 'name'

    name = fields.Char(string='Insurer Name', required=True)
    code = fields.Char(string='Code')
    payer_id = fields.Char(string='EDI Payer ID')
    phone = fields.Char(string='Phone')
    email = fields.Char(string='Email')
    website = fields.Char(string='Website')
    active = fields.Boolean(default=True)
    plan_ids = fields.One2many('clinic.insurer.plan', 'insurer_id', string='Plans')
    notes = fields.Text(string='Notes')
