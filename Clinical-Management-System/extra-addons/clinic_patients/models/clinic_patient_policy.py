from odoo import fields, models


class ClinicPatientPolicy(models.Model):
    _name = 'clinic.patient.policy'
    _description = 'Patient Insurance Policy'
    _order = 'patient_id, is_primary desc'

    patient_id = fields.Many2one('clinic.patient', string='Patient', required=True, ondelete='cascade')
    insurer_id = fields.Many2one('clinic.insurer', string='Insurer', required=True)
    plan_id = fields.Many2one('clinic.insurer.plan', string='Plan',
                              domain="[('insurer_id','=',insurer_id)]")
    policy_number = fields.Char(string='Policy Number', required=True)
    group_number = fields.Char(string='Group Number')
    member_id = fields.Char(string='Member ID')
    effective_date = fields.Date(string='Effective Date')
    termination_date = fields.Date(string='Termination Date')
    is_primary = fields.Boolean(string='Primary Insurance', default=True)
    state = fields.Selection([('active', 'Active'), ('inactive', 'Inactive'), ('expired', 'Expired')],
                             string='Status', default='active')
