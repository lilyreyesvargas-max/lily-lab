from odoo import fields, models


class ClinicPhysicianSchedule(models.Model):
    _name = 'clinic.physician.schedule'
    _description = 'Physician Weekly Schedule'
    _order = 'physician_id, day_of_week'

    physician_id = fields.Many2one('res.users', string='Physician', required=True)
    company_id = fields.Many2one('res.company', string='Branch', required=True,
                                 default=lambda self: self.env.company)
    specialty_id = fields.Many2one('clinic.specialty', string='Specialty')
    day_of_week = fields.Selection([
        ('0', 'Monday'), ('1', 'Tuesday'), ('2', 'Wednesday'),
        ('3', 'Thursday'), ('4', 'Friday'), ('5', 'Saturday'), ('6', 'Sunday'),
    ], string='Day', required=True)
    hour_from = fields.Float(string='From', default=8.0)
    hour_to = fields.Float(string='To', default=17.0)
    active = fields.Boolean(default=True)
