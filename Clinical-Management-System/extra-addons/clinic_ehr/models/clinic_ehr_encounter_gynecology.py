from datetime import date

from odoo import api, fields, models


class ClinicEhrEncounterGynecology(models.Model):
    _name = 'clinic.ehr.encounter.gynecology'
    _description = 'Gynecology Encounter Extension'

    encounter_id = fields.Many2one(
        'clinic.ehr.encounter', string='Encounter',
        required=True, ondelete='cascade', index=True)
    lmp = fields.Date(string='Last Menstrual Period')
    gravida = fields.Integer(string='Gravida', default=0)
    para = fields.Integer(string='Para', default=0)
    abortus = fields.Integer(string='Abortus', default=0)
    gestational_age = fields.Integer(
        string='Gestational Age (days)',
        compute='_compute_gestational_age', store=True)
    gynecological_notes = fields.Text(string='Notes')

    @api.depends('lmp')
    def _compute_gestational_age(self):
        today = date.today()
        for rec in self:
            rec.gestational_age = (today - rec.lmp).days if rec.lmp else 0
