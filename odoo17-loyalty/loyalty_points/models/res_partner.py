from odoo import models, fields, api

class ResPartner(models.Model):
    _inherit = 'res.partner'

    loyalty_points = fields.Float(string='Loyalty Points', compute='_compute_loyalty_points', store=True)

    def _compute_loyalty_points(self):
        for partner in self:
            transactions = self.env['loyalty.transaction'].search([('partner_id', '=', partner.id)])
            earn = sum(t.points for t in transactions if t.type == 'earn')
            spend = sum(t.points for t in transactions if t.type == 'spend')
            partner.loyalty_points = earn - spend

    def action_view_loyalty_transactions(self):
        self.ensure_one()
        return {
            'name': 'Loyalty Transactions',
            'type': 'ir.actions.act_window',
            'res_model': 'loyalty.transaction',
            'view_mode': 'tree,form',
            'domain': [('partner_id', '=', self.id)],
            'context': {'default_partner_id': self.id},
        }
