from odoo import api, fields, models


class ClinicSupplyRequest(models.Model):
    _name = 'clinic.supply.request'
    _description = 'Internal Supply Request'
    _order = 'request_date desc'
    _inherit = ['mail.thread', 'mail.activity.mixin']

    name = fields.Char(string='Request No.', readonly=True, copy=False, default='New')
    company_id = fields.Many2one('res.company', string='Branch', required=True,
                                 default=lambda self: self.env.company)
    requested_by = fields.Many2one('res.users', string='Requested By',
                                   default=lambda self: self.env.user)
    approved_by = fields.Many2one('res.users', string='Approved By', readonly=True)
    request_date = fields.Date(string='Request Date', default=fields.Date.today)
    required_date = fields.Date(string='Required By')
    state = fields.Selection([
        ('draft', 'Draft'), ('submitted', 'Submitted'),
        ('approved', 'Approved'), ('rejected', 'Rejected'),
        ('transferred', 'Transferred'), ('consumed', 'Consumed'),
    ], string='Status', default='draft', tracking=True)
    line_ids = fields.One2many('clinic.supply.request.line', 'request_id', string='Items')
    picking_id = fields.Many2one('stock.picking', string='Transfer', readonly=True)
    notes = fields.Text(string='Notes')

    @api.model_create_multi
    def create(self, vals_list):
        for vals in vals_list:
            if vals.get('name', 'New') == 'New':
                vals['name'] = self.env['ir.sequence'].next_by_code('clinic.supply.request') or 'New'
        return super().create(vals_list)

    def action_submit(self):
        self.state = 'submitted'

    def action_approve(self):
        self.write({'state': 'approved', 'approved_by': self.env.uid})

    def action_reject(self):
        self.state = 'rejected'

    def action_create_transfer(self):
        self.ensure_one()
        if not self.line_ids:
            return
        picking_type = self.env['stock.picking.type'].search(
            [('code', '=', 'internal'), ('company_id', '=', self.company_id.id)], limit=1
        ) or self.env['stock.picking.type'].search([('code', '=', 'outgoing')], limit=1)

        if picking_type:
            moves = [(0, 0, {
                'name': line.product_id.name,
                'product_id': line.product_id.id,
                'product_uom_qty': line.quantity_approved or line.quantity_requested,
                'product_uom': line.product_id.uom_id.id,
                'location_id': (picking_type.default_location_src_id.id
                                or self.env.ref('stock.stock_location_stock').id),
                'location_dest_id': (picking_type.default_location_dest_id.id
                                     or self.env.ref('stock.stock_location_stock').id),
            }) for line in self.line_ids]
            picking = self.env['stock.picking'].create({
                'picking_type_id': picking_type.id,
                'company_id': self.company_id.id,
                'move_ids': moves,
            })
            self.write({'picking_id': picking.id, 'state': 'transferred'})

    def action_mark_consumed(self):
        self.state = 'consumed'

    def action_reset_draft(self):
        self.state = 'draft'


class ClinicSupplyRequestLine(models.Model):
    _name = 'clinic.supply.request.line'
    _description = 'Supply Request Line'

    request_id = fields.Many2one('clinic.supply.request', required=True, ondelete='cascade')
    product_id = fields.Many2one('product.product', string='Product', required=True)
    quantity_requested = fields.Float(string='Requested Qty', default=1.0)
    quantity_approved = fields.Float(string='Approved Qty')
    uom_id = fields.Many2one(related='product_id.uom_id', string='Unit', store=True)
    notes = fields.Text(string='Notes')
