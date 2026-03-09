from odoo import fields, models


class ClinicAutomationLog(models.Model):
    _name = 'clinic.automation.log'
    _description = 'Clinic Automation Job Log'
    _order = 'create_date desc'
    _rec_name = 'job_type'

    job_type = fields.Selection([
        ('appointment_reminder', 'Appointment Reminder'),
        ('stock_alert', 'Stock Alert'),
        ('expiry_alert', 'Expiry Alert'),
        ('edi_send', 'EDI Send'),
        ('edi_import', 'EDI Import'),
        ('other', 'Other'),
    ], string='Job Type', required=True)

    company_id = fields.Many2one('res.company', string='Company',
                                 default=lambda self: self.env.company)
    result = fields.Selection([
        ('success', 'Success'),
        ('warning', 'Warning'),
        ('error', 'Error'),
    ], string='Result', required=True, default='success')

    message = fields.Text(string='Message')
    records_processed = fields.Integer(string='Records Processed', default=0)
    duration_ms = fields.Integer(string='Duration (ms)', default=0)
    create_date = fields.Datetime(string='Run At', readonly=True)
