from odoo import api, fields, models


class ClinicIcd10(models.Model):
    _name = 'clinic.icd10'
    _description = 'ICD-10 Diagnosis Code'
    _order = 'code'
    _rec_name = 'code'

    code = fields.Char(string='Code', required=True, index=True)
    description = fields.Char(string='Description', required=True)
    category = fields.Char(string='Category')
    active = fields.Boolean(default=True)

    _sql_constraints = [
        ('code_uniq', 'unique(code)', 'ICD-10 code must be unique'),
    ]

    def name_get(self):
        return [(rec.id, f"{rec.code} — {rec.description}") for rec in self]

    @api.model
    def _name_search(self, name='', args=None, operator='ilike', limit=100, name_get_uid=None):
        domain = args or []
        if name:
            domain = ['|', ('code', operator, name), ('description', operator, name)] + domain
        return self._search(domain, limit=limit, access_rights_uid=name_get_uid)
