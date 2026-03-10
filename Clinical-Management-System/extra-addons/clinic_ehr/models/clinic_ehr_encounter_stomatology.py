from odoo import fields, models


class ClinicEhrEncounterStomatology(models.Model):
    _name = 'clinic.ehr.encounter.stomatology'
    _description = 'Stomatology Encounter Extension'

    encounter_id = fields.Many2one(
        'clinic.ehr.encounter', string='Encounter',
        required=True, ondelete='cascade', index=True)
    tooth_chart = fields.Text(string='Tooth Chart (JSON)')
    periodontal_notes = fields.Text(string='Periodontal Notes')
    dental_procedure = fields.Text(string='Dental Procedure')
