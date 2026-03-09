from odoo import fields, models


class ClinicEdiEligibilityRequest(models.Model):
    _name = 'clinic.edi.eligibility.request'
    _description = 'EDI Eligibility Request (270/271)'
    _order = 'request_date desc'
    _inherit = ['mail.thread']

    patient_id = fields.Many2one('clinic.patient', string='Patient', required=True)
    insurer_id = fields.Many2one('clinic.insurer', string='Insurer', required=True)
    request_date = fields.Date(string='Request Date', default=fields.Date.today)
    state = fields.Selection([
        ('draft', 'Draft'), ('sent', 'Sent'),
        ('received', 'Response Received'), ('processed', 'Processed'),
    ], string='Status', default='draft', tracking=True)
    transaction_270_id = fields.Many2one('clinic.edi.transaction', string='270 Transaction',
                                         domain=[('transaction_type', '=', '270')])
    transaction_271_id = fields.Many2one('clinic.edi.transaction', string='271 Response',
                                         domain=[('transaction_type', '=', '271')])
    eligible = fields.Boolean(string='Eligible', readonly=True)
    eligibility_notes = fields.Text(string='Eligibility Notes', readonly=True)
    plan_name = fields.Char(string='Plan Name', readonly=True)
    company_id = fields.Many2one('res.company', string='Branch',
                                 default=lambda self: self.env.company)

    def action_generate_270(self):
        """Generate and send 270 eligibility request."""
        from ..utils.edi_generator import generate_270
        for rec in self:
            config = self.env['clinic.edi.config'].search([('active', '=', True)], limit=1)
            content = generate_270(rec, config)
            tx = self.env['clinic.edi.transaction'].create({
                'transaction_type': '270',
                'direction': 'outbound',
                'content': content,
                'claim_id': False,
                'edi_config_id': config.id if config else False,
            })
            rec.write({'transaction_270_id': tx.id})
            tx.action_validate()
            if tx.state == 'validated':
                tx.action_send_rest()
                rec.state = 'sent'
