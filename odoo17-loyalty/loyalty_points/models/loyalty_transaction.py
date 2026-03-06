from odoo import models, fields, api

class LoyaltyTransaction(models.Model):
    _name = 'loyalty.transaction'
    _description = 'Loyalty Points Transaction'
    _order = 'date desc'

    partner_id = fields.Many2one('res.partner', string='Customer', required=True, ondelete='cascade')
    date = fields.Datetime(string='Date', default=fields.Datetime.now, required=True)
    type = fields.Selection([
        ('earn', 'Earned'),
        ('spend', 'Spent')
    ], string='Type', required=True)
    points = fields.Float(string='Points', required=True)
    origin = fields.Char(string='Source', help='Source document (e.g., Sale Order number)')
    note = fields.Text(string='Notes')

    @api.model
    def create(self, vals):
        # Prevent creating negative points transactions or invalid types
        if vals.get('points', 0) < 0:
            vals['points'] = abs(vals['points'])
        return super(LoyaltyTransaction, self).create(vals)
