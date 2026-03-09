from odoo import api, fields, models


class ClinicShiftDay(models.Model):
    _name = 'clinic.shift.day'
    _description = 'Day of Week'
    _order = 'sequence'

    name = fields.Char(string='Day', required=True)
    code = fields.Char(string='Code')
    sequence = fields.Integer(default=1)


class ClinicShift(models.Model):
    _name = 'clinic.shift'
    _description = 'Work Shift'
    _order = 'name'

    name = fields.Char(string='Shift Name', required=True)
    company_id = fields.Many2one('res.company', string='Branch',
                                 default=lambda self: self.env.company)
    hour_from = fields.Float(string='Start Hour', default=8.0)
    hour_to = fields.Float(string='End Hour', default=17.0)
    duration = fields.Float(string='Duration (h)', compute='_compute_duration', store=True)
    day_ids = fields.Many2many('clinic.shift.day', string='Days')
    active = fields.Boolean(default=True)

    @api.depends('hour_from', 'hour_to')
    def _compute_duration(self):
        for rec in self:
            rec.duration = max(0.0, rec.hour_to - rec.hour_from)
