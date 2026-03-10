from odoo import fields, models


class ClinicEhrEncounterOphthalmology(models.Model):
    _name = 'clinic.ehr.encounter.ophthalmology'
    _description = 'Ophthalmology Encounter Extension'

    encounter_id = fields.Many2one(
        'clinic.ehr.encounter', string='Encounter',
        required=True, ondelete='cascade', index=True)
    od_sphere = fields.Float(string='OD Sphere', digits=(5, 2))
    od_cylinder = fields.Float(string='OD Cylinder', digits=(5, 2))
    od_axis = fields.Integer(string='OD Axis')
    od_va = fields.Char(string='OD Visual Acuity')
    iop_od = fields.Float(string='IOP OD (mmHg)', digits=(5, 1))
    oi_sphere = fields.Float(string='OI Sphere', digits=(5, 2))
    oi_cylinder = fields.Float(string='OI Cylinder', digits=(5, 2))
    oi_axis = fields.Integer(string='OI Axis')
    oi_va = fields.Char(string='OI Visual Acuity')
    iop_oi = fields.Float(string='IOP OI (mmHg)', digits=(5, 1))
    ophthalmic_notes = fields.Text(string='Notes')
