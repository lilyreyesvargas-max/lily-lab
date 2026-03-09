from odoo import fields, models


class ClinicEdiConfig(models.Model):
    _name = 'clinic.edi.config'
    _description = 'EDI Clearinghouse Configuration'
    _order = 'name'

    name = fields.Char(string='Configuration Name', required=True)
    company_id = fields.Many2one('res.company', string='Branch',
                                 default=lambda self: self.env.company)
    clearinghouse_name = fields.Char(string='Clearinghouse Name')
    transport_type = fields.Selection([
        ('rest', 'REST API'), ('sftp', 'SFTP'), ('both', 'REST + SFTP'),
    ], string='Transport', default='rest')
    rest_url = fields.Char(string='REST Base URL')
    rest_api_key = fields.Char(string='API Key')
    sftp_host = fields.Char(string='SFTP Host')
    sftp_port = fields.Integer(string='SFTP Port', default=22)
    sftp_user = fields.Char(string='SFTP User')
    sftp_password = fields.Char(string='SFTP Password')
    sftp_in_path = fields.Char(string='SFTP Inbound Path', default='/edi/in')
    sftp_out_path = fields.Char(string='SFTP Outbound Path', default='/edi/out')
    sender_id = fields.Char(string='Sender ID (ISA06)', default='SENDCLINIC')
    receiver_id = fields.Char(string='Receiver ID (ISA08)', default='CLRHOUSE')
    is_sandbox = fields.Boolean(string='Sandbox Mode', default=True)
    active = fields.Boolean(default=True)
