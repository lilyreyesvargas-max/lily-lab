from odoo import fields, models


class ClinicEhrDiagnosis(models.Model):
    _name = 'clinic.ehr.diagnosis'
    _description = 'Encounter Diagnosis'
    _order = 'diagnosis_type'

    encounter_id = fields.Many2one('clinic.ehr.encounter', required=True, ondelete='cascade')
    icd10_id = fields.Many2one('clinic.icd10', string='ICD-10 Code', required=True)
    diagnosis_type = fields.Selection([
        ('primary', 'Primary'), ('secondary', 'Secondary'), ('tertiary', 'Tertiary'),
    ], string='Type', default='primary')
    notes = fields.Text(string='Notes')
