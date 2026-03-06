from odoo import models, fields, api

class LoyaltyRule(models.Model):
    _name = 'loyalty.rule'
    _description = 'Loyalty Points Rule'

    name = fields.Char(string='Rule Name', required=True)
    active = fields.Boolean(default=True)
    min_amount = fields.Float(string='Minimum Amount', default=0.0, help='Minimum purchase amount to earn points.')
    ratio_amount = fields.Float(string='Earn Ratio', default=1.0, help='Points earned per currency unit spent.')
    ratio_redeem = fields.Float(string='Redeem Ratio', default=1.0, help='Discount amount per point redeemed.')

    _sql_constraints = [
        ('name_uniq', 'unique (name)', 'Rule name must be unique!')
    ]
