from odoo import api, fields, models


class ClinicBillingClaim(models.Model):
    _name = 'clinic.billing.claim'
    _description = 'Billing Claim'
    _order = 'service_date desc'
    _inherit = ['mail.thread', 'mail.activity.mixin']

    name = fields.Char(string='Claim No.', readonly=True, copy=False, default='New')
    patient_id = fields.Many2one('clinic.patient', string='Patient', required=True, tracking=True)
    company_id = fields.Many2one('res.company', string='Branch',
                                 default=lambda self: self.env.company)
    encounter_ref = fields.Char(string='Encounter Ref.')
    policy_id = fields.Many2one('clinic.patient.policy', string='Insurance Policy', required=True,
                                domain="[('patient_id','=',patient_id)]")
    service_date = fields.Date(string='Service Date', required=True)
    currency_id = fields.Many2one('res.currency', string='Currency',
                                  default=lambda self: self.env.company.currency_id)
    total_amount = fields.Monetary(string='Total Billed', compute='_compute_totals', store=True)
    insurer_amount = fields.Monetary(string='Insurer Portion', compute='_compute_totals', store=True)
    patient_amount = fields.Monetary(string='Patient Responsibility', compute='_compute_totals', store=True)
    state = fields.Selection([
        ('draft', 'Draft'), ('submitted', 'Submitted'), ('partial', 'Partial'),
        ('paid', 'Paid'), ('denied', 'Denied'), ('voided', 'Voided'),
    ], string='Status', default='draft', tracking=True)
    line_ids = fields.One2many('clinic.billing.claim.line', 'claim_id', string='Claim Lines')
    invoice_id = fields.Many2one('account.move', string='Patient Invoice', readonly=True)
    notes = fields.Text(string='Notes')

    @api.depends('line_ids', 'line_ids.subtotal', 'line_ids.insurer_amount', 'line_ids.patient_amount')
    def _compute_totals(self):
        for rec in self:
            rec.total_amount = sum(rec.line_ids.mapped('subtotal'))
            rec.insurer_amount = sum(rec.line_ids.mapped('insurer_amount'))
            rec.patient_amount = sum(rec.line_ids.mapped('patient_amount'))

    @api.model_create_multi
    def create(self, vals_list):
        for vals in vals_list:
            if vals.get('name', 'New') == 'New':
                vals['name'] = self.env['ir.sequence'].next_by_code('clinic.billing.claim') or 'New'
        return super().create(vals_list)

    def action_submit(self):
        self.state = 'submitted'

    def action_mark_paid(self):
        self.state = 'paid'

    def action_mark_partial(self):
        self.state = 'partial'

    def action_deny(self):
        self.state = 'denied'

    def action_void(self):
        self.state = 'voided'

    def action_reset_draft(self):
        self.state = 'draft'


class ClinicBillingClaimLine(models.Model):
    _name = 'clinic.billing.claim.line'
    _description = 'Claim Line Item'

    claim_id = fields.Many2one('clinic.billing.claim', required=True, ondelete='cascade')
    service_code = fields.Char(string='CPT Code', required=True)
    description = fields.Char(string='Description')
    quantity = fields.Float(string='Qty', default=1.0)
    currency_id = fields.Many2one(related='claim_id.currency_id', store=True)
    unit_price = fields.Monetary(string='Unit Price')
    subtotal = fields.Monetary(string='Subtotal', compute='_compute_subtotal', store=True)
    coverage_pct = fields.Float(string='Coverage %', default=80.0, digits=(5, 2))
    insurer_amount = fields.Monetary(string='Insurer', compute='_compute_split', store=True)
    patient_amount = fields.Monetary(string='Patient', compute='_compute_split', store=True)
    icd10_id = fields.Many2one('clinic.icd10', string='ICD-10')

    @api.depends('quantity', 'unit_price')
    def _compute_subtotal(self):
        for rec in self:
            rec.subtotal = rec.quantity * rec.unit_price

    @api.depends('subtotal', 'coverage_pct')
    def _compute_split(self):
        for rec in self:
            rec.insurer_amount = rec.subtotal * rec.coverage_pct / 100.0
            rec.patient_amount = rec.subtotal - rec.insurer_amount
