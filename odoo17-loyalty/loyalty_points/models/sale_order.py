from odoo import models, fields, api, _
from odoo.exceptions import ValidationError

class SaleOrder(models.Model):
    _inherit = 'sale.order'

    loyalty_points_to_redeem = fields.Float(string='Points to Redeem', default=0.0)
    loyalty_discount = fields.Monetary(string='Loyalty Discount', compute='_compute_loyalty_discount', store=True)

    @api.depends('loyalty_points_to_redeem')
    def _compute_loyalty_discount(self):
        # We take the first active rule to get the ratio_redeem
        rule = self.env['loyalty.rule'].search([('active', '=', True)], limit=1)
        for order in self:
            if rule and order.loyalty_points_to_redeem > 0:
                order.loyalty_discount = order.loyalty_points_to_redeem * rule.ratio_redeem
            else:
                order.loyalty_discount = 0.0

    def action_apply_loyalty_points(self):
        for order in self:
            if order.loyalty_points_to_redeem > order.partner_id.loyalty_points:
                raise ValidationError(_("Insufficient loyalty points! Balance: %s") % order.partner_id.loyalty_points)
            # This triggers the compute
            order._compute_loyalty_discount()
        return True

    def action_confirm(self):
        res = super(SaleOrder, self).action_confirm()
        for order in self:
            # 1. Register Spend Transaction if points were redeemed
            if order.loyalty_points_to_redeem > 0:
                self.env['loyalty.transaction'].create({
                    'partner_id': order.partner_id.id,
                    'type': 'spend',
                    'points': order.loyalty_points_to_redeem,
                    'origin': order.name,
                    'note': _('Redemption for order %s') % order.name
                })

            # 2. Register Earn Transaction based on rules
            rule = self.env['loyalty.rule'].search([
                ('active', '=', True),
                ('min_amount', '<=', order.amount_total)
            ], order='min_amount desc', limit=1)
            
            if rule:
                points_earned = order.amount_total * rule.ratio_amount
                if points_earned > 0:
                    self.env['loyalty.transaction'].create({
                        'partner_id': order.partner_id.id,
                        'type': 'earn',
                        'points': points_earned,
                        'origin': order.name,
                        'note': _('Earned from order %s (Rule: %s)') % (order.name, rule.name)
                    })
        return res
