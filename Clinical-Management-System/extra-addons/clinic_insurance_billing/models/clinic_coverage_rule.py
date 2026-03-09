from odoo import fields, models


class ClinicCoverageRule(models.Model):
    _name = 'clinic.coverage.rule'
    _description = 'Insurance Coverage Rule'
    _order = 'insurer_id, service_code'

    name = fields.Char(string='Rule Name', required=True)
    insurer_id = fields.Many2one('clinic.insurer', string='Insurer', required=True)
    plan_id = fields.Many2one('clinic.insurer.plan', string='Plan',
                              domain="[('insurer_id','=',insurer_id)]")
    service_code = fields.Char(string='CPT/Service Code')
    coverage_pct = fields.Float(string='Coverage %', digits=(5, 2), default=80.0)
    currency_id = fields.Many2one('res.currency', string='Currency',
                                  default=lambda self: self.env.company.currency_id)
    patient_copay = fields.Monetary(string='Patient Copay')
    patient_coinsurance_pct = fields.Float(string='Patient Coinsurance %', digits=(5, 2))
    requires_auth = fields.Boolean(string='Requires Prior Auth')
    auth_code = fields.Char(string='Auth Code')
    active = fields.Boolean(default=True)
    notes = fields.Text(string='Notes')
