from odoo import api, fields, models


class ClinicEhrEncounter(models.Model):
    _name = 'clinic.ehr.encounter'
    _description = 'Clinical Encounter'
    _order = 'encounter_date desc'
    _inherit = ['mail.thread', 'mail.activity.mixin']

    ref = fields.Char(string='Encounter No.', readonly=True, copy=False, default='New')
    name = fields.Char(string='Name', compute='_compute_name', store=True)
    patient_id = fields.Many2one('clinic.patient', string='Patient', required=True, tracking=True)
    company_id = fields.Many2one('res.company', string='Branch',
                                 default=lambda self: self.env.company)
    physician_id = fields.Many2one('res.users', string='Attending Physician', tracking=True)
    specialty_id = fields.Many2one('clinic.specialty', string='Specialty')
    encounter_date = fields.Datetime(string='Date', default=fields.Datetime.now, tracking=True)
    chief_complaint = fields.Text(string='Chief Complaint')
    history_present_illness = fields.Text(string='HPI')
    physical_exam = fields.Text(string='Physical Examination')
    assessment = fields.Text(string='Assessment')
    plan = fields.Text(string='Plan')
    diagnosis_ids = fields.One2many('clinic.ehr.diagnosis', 'encounter_id', string='Diagnoses')
    attachment_ids = fields.Many2many('ir.attachment', string='Attachments')
    policy_id = fields.Many2one('clinic.patient.policy', string='Insurance Policy',
                                domain="[('patient_id','=',patient_id)]")
    state = fields.Selection([
        ('draft', 'Draft'), ('in_progress', 'In Progress'),
        ('completed', 'Completed'), ('cancelled', 'Cancelled'),
    ], string='Status', default='draft', tracking=True)
    notes = fields.Text(string='Internal Notes')
    # Vital signs — General Medicine
    bp_systolic = fields.Integer(string='BP Systolic')
    bp_diastolic = fields.Integer(string='BP Diastolic')
    heart_rate = fields.Integer(string='Heart Rate (bpm)')
    temperature = fields.Float(string='Temperature (°F)', digits=(5, 1))
    weight = fields.Float(string='Weight (kg)', digits=(5, 1))
    height = fields.Float(string='Height (cm)', digits=(5, 1))
    bmi = fields.Float(string='BMI', compute='_compute_bmi', store=True, digits=(5, 1))
    respiratory_rate = fields.Integer(string='Respiratory Rate')
    oxygen_saturation = fields.Float(string='O2 Saturation (%)', digits=(5, 1))
    # Specialty extensions (One2many to dedicated extension models)
    gynecology_ids = fields.One2many(
        'clinic.ehr.encounter.gynecology', 'encounter_id', string='Gynecology')
    ophthalmology_ids = fields.One2many(
        'clinic.ehr.encounter.ophthalmology', 'encounter_id', string='Ophthalmology')
    stomatology_ids = fields.One2many(
        'clinic.ehr.encounter.stomatology', 'encounter_id', string='Stomatology')

    @api.depends('ref', 'patient_id')
    def _compute_name(self):
        for rec in self:
            rec.name = f"{rec.ref or 'New'} — {rec.patient_id.name or ''}"

    @api.depends('weight', 'height')
    def _compute_bmi(self):
        for rec in self:
            if rec.weight and rec.height:
                h_m = rec.height / 100.0
                rec.bmi = round(rec.weight / (h_m * h_m), 1)
            else:
                rec.bmi = 0.0

    @api.model_create_multi
    def create(self, vals_list):
        for vals in vals_list:
            if vals.get('ref', 'New') == 'New':
                vals['ref'] = self.env['ir.sequence'].next_by_code('clinic.ehr.encounter') or 'New'
        return super().create(vals_list)

    def action_start(self):
        self.state = 'in_progress'

    def action_complete(self):
        self.state = 'completed'

    def action_cancel(self):
        self.state = 'cancelled'

    def action_reset_draft(self):
        self.state = 'draft'
