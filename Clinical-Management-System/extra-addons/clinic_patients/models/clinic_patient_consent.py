from odoo import fields, models


class ClinicPatientConsent(models.Model):
    _name = 'clinic.patient.consent'
    _description = 'Patient Consent'
    _order = 'patient_id, signed_date desc'

    patient_id = fields.Many2one('clinic.patient', string='Patient', required=True, ondelete='cascade')
    consent_type = fields.Selection([
        ('treatment', 'Treatment Consent'),
        ('privacy', 'Privacy Notice (HIPAA)'),
        ('billing', 'Billing Authorization'),
        ('research', 'Research Consent'),
    ], string='Consent Type', required=True)
    signed_date = fields.Date(string='Signed Date')
    expiry_date = fields.Date(string='Expiry Date')
    signed_by = fields.Char(string='Signed By (Witness/Representative)')
    notes = fields.Text(string='Notes')
