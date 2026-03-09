from odoo import fields, models


class ClinicRemittance(models.Model):
    _name = 'clinic.remittance'
    _description = '835 Remittance Advice (ERA)'
    _order = 'payment_date desc'

    name = fields.Char(string='Remittance No.', required=True)
    insurer_id = fields.Many2one('clinic.insurer', string='Insurer')
    payment_date = fields.Date(string='Payment Date')
    check_eft_number = fields.Char(string='Check / EFT No.')
    currency_id = fields.Many2one('res.currency', string='Currency',
                                  default=lambda self: self.env.company.currency_id)
    total_paid = fields.Monetary(string='Total Paid')
    state = fields.Selection([
        ('pending', 'Pending'), ('processed', 'Processed'), ('reconciled', 'Reconciled'),
    ], string='Status', default='pending')
    line_ids = fields.One2many('clinic.remittance.line', 'remittance_id', string='Lines')
    notes = fields.Text(string='Notes')


class ClinicRemittanceLine(models.Model):
    _name = 'clinic.remittance.line'
    _description = 'Remittance Line'

    remittance_id = fields.Many2one('clinic.remittance', required=True, ondelete='cascade')
    claim_id = fields.Many2one('clinic.billing.claim', string='Claim')
    claim_ref = fields.Char(string='Claim Reference')
    patient_name = fields.Char(string='Patient Name')
    currency_id = fields.Many2one(related='remittance_id.currency_id', store=True)
    billed_amount = fields.Monetary(string='Billed')
    paid_amount = fields.Monetary(string='Paid')
    adjustment_reason = fields.Char(string='Adj. Reason Code')
    status = fields.Selection([
        ('paid', 'Paid'), ('partial', 'Partial'), ('denied', 'Denied'), ('reversed', 'Reversed'),
    ], string='Status', default='paid')
