from odoo import fields, models


class ClinicInsurerPlan(models.Model):
    _name = 'clinic.insurer.plan'
    _description = 'Insurance Plan'
    _order = 'insurer_id, name'

    insurer_id = fields.Many2one('clinic.insurer', string='Insurer', required=True, ondelete='cascade')
    name = fields.Char(string='Plan Name', required=True)
    plan_code = fields.Char(string='Plan Code')
    coverage_type = fields.Selection([
        ('hmo', 'HMO'), ('ppo', 'PPO'), ('epo', 'EPO'),
        ('pos', 'POS'), ('other', 'Other'),
    ], string='Coverage Type', default='ppo')
    currency_id = fields.Many2one('res.currency', string='Currency',
                                  default=lambda self: self.env.company.currency_id)
    deductible = fields.Monetary(string='Annual Deductible')
    copay = fields.Monetary(string='Copay per Visit')
    coinsurance = fields.Float(string='Coinsurance %', digits=(5, 2))
    active = fields.Boolean(default=True)
