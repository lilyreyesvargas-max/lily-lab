from odoo import api, fields, models
from datetime import date


class ClinicPatient(models.Model):
    _name = 'clinic.patient'
    _description = 'Patient'
    _order = 'name'
    _inherit = ['mail.thread', 'mail.activity.mixin']

    name = fields.Char(string='Full Name', required=True, tracking=True)
    ref = fields.Char(string='Patient ID', readonly=True, copy=False, default='New')
    company_id = fields.Many2one('res.company', string='Branch', required=True,
                                 default=lambda self: self.env.company)
    date_of_birth = fields.Date(string='Date of Birth')
    age = fields.Integer(string='Age', compute='_compute_age', store=False)
    gender = fields.Selection([('male', 'Male'), ('female', 'Female'), ('other', 'Other')],
                              string='Gender')
    blood_type = fields.Selection([
        ('A+', 'A+'), ('A-', 'A-'), ('B+', 'B+'), ('B-', 'B-'),
        ('AB+', 'AB+'), ('AB-', 'AB-'), ('O+', 'O+'), ('O-', 'O-'),
    ], string='Blood Type')
    phone = fields.Char(string='Phone')
    mobile = fields.Char(string='Mobile')
    email = fields.Char(string='Email')
    address = fields.Text(string='Address')
    policy_ids = fields.One2many('clinic.patient.policy', 'patient_id', string='Insurance Policies')
    consent_ids = fields.One2many('clinic.patient.consent', 'patient_id', string='Consents')
    active = fields.Boolean(default=True)
    notes = fields.Text(string='Clinical Notes')

    @api.depends('date_of_birth')
    def _compute_age(self):
        today = date.today()
        for rec in self:
            if rec.date_of_birth:
                d = rec.date_of_birth
                rec.age = today.year - d.year - ((today.month, today.day) < (d.month, d.day))
            else:
                rec.age = 0

    @api.model_create_multi
    def create(self, vals_list):
        for vals in vals_list:
            if vals.get('ref', 'New') == 'New':
                vals['ref'] = self.env['ir.sequence'].next_by_code('clinic.patient.ref') or 'New'
        return super().create(vals_list)
