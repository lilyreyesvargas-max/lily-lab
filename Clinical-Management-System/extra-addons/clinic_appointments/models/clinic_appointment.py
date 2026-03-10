from datetime import timedelta

from odoo import api, fields, models
from odoo.exceptions import ValidationError


class ClinicAppointment(models.Model):
    _name = 'clinic.appointment'
    _description = 'Patient Appointment'
    _order = 'appointment_date desc'
    _inherit = ['mail.thread', 'mail.activity.mixin']

    name = fields.Char(string='Appointment No.', readonly=True, copy=False, default='New')
    patient_id = fields.Many2one('clinic.patient', string='Patient', required=True, tracking=True)
    company_id = fields.Many2one('res.company', string='Branch', required=True,
                                 default=lambda self: self.env.company)
    physician_id = fields.Many2one('res.users', string='Physician', required=True, tracking=True)
    specialty_id = fields.Many2one('clinic.specialty', string='Specialty')
    appointment_date = fields.Datetime(string='Date & Time', required=True, tracking=True)
    duration = fields.Float(string='Duration (h)', default=0.5)
    date_end = fields.Datetime(string='End Time', compute='_compute_date_end', store=True)
    state = fields.Selection([
        ('scheduled', 'Scheduled'), ('confirmed', 'Confirmed'),
        ('arrived', 'Arrived'), ('in_consultation', 'In Consultation'),
        ('completed', 'Completed'), ('cancelled', 'Cancelled'), ('no_show', 'No Show'),
    ], string='Status', default='scheduled', tracking=True)
    reason = fields.Text(string='Reason for Visit')
    notes = fields.Text(string='Internal Notes')
    policy_id = fields.Many2one('clinic.patient.policy', string='Insurance Policy',
                                domain="[('patient_id','=',patient_id)]")
    color = fields.Integer(string='Color', compute='_compute_color', store=True)

    @api.depends('appointment_date', 'duration')
    def _compute_date_end(self):
        for rec in self:
            if rec.appointment_date and rec.duration:
                rec.date_end = rec.appointment_date + timedelta(hours=rec.duration)
            else:
                rec.date_end = rec.appointment_date

    @api.depends('state')
    def _compute_color(self):
        colors = {
            'scheduled': 1, 'confirmed': 4, 'arrived': 2,
            'in_consultation': 3, 'completed': 10, 'cancelled': 9, 'no_show': 8,
        }
        for rec in self:
            rec.color = colors.get(rec.state, 0)

    @api.constrains('physician_id', 'appointment_date', 'date_end')
    def _check_physician_overlap(self):
        """Prevent double-booking: same physician cannot have two overlapping active appointments."""
        for record in self:
            if not record.physician_id or not record.appointment_date:
                continue
            overlap = self.search([
                ('id', '!=', record.id),
                ('physician_id', '=', record.physician_id.id),
                ('state', 'not in', ['cancelled', 'no_show']),
                ('appointment_date', '<', record.date_end),
                ('date_end', '>', record.appointment_date),
            ])
            if overlap:
                raise ValidationError(
                    f"Dr. {record.physician_id.name} already has an appointment "
                    f"overlapping {record.appointment_date}."
                )

    @api.model_create_multi
    def create(self, vals_list):
        for vals in vals_list:
            if vals.get('name', 'New') == 'New':
                vals['name'] = self.env['ir.sequence'].next_by_code('clinic.appointment') or 'New'
        return super().create(vals_list)

    def action_confirm(self):
        self.state = 'confirmed'

    def action_arrived(self):
        self.state = 'arrived'

    def action_start(self):
        self.state = 'in_consultation'

    def action_complete(self):
        self.write({'state': 'completed'})

    def action_cancel(self):
        self.state = 'cancelled'

    def action_no_show(self):
        self.state = 'no_show'

    def action_reset_scheduled(self):
        self.state = 'scheduled'
