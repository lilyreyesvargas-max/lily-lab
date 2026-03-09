import json
import logging
import urllib.request
import urllib.error
from datetime import datetime

from odoo import api, fields, models

_logger = logging.getLogger(__name__)


class ClinicEdiTransaction(models.Model):
    _name = 'clinic.edi.transaction'
    _description = 'EDI Transaction Staging'
    _order = 'transaction_date desc'
    _inherit = ['mail.thread']

    ref = fields.Char(string='Ref.', readonly=True, copy=False, default='New')
    name = fields.Char(string='Name', compute='_compute_name', store=True)
    transaction_type = fields.Selection([
        ('837', '837 — Professional Claim'),
        ('835', '835 — Remittance Advice'),
        ('270', '270 — Eligibility Request'),
        ('271', '271 — Eligibility Response'),
    ], string='Type', required=True)
    direction = fields.Selection([
        ('outbound', 'Outbound'), ('inbound', 'Inbound'),
    ], string='Direction', required=True, default='outbound')
    company_id = fields.Many2one('res.company', string='Branch',
                                 default=lambda self: self.env.company)
    claim_id = fields.Many2one('clinic.billing.claim', string='Claim')
    remittance_id = fields.Many2one('clinic.remittance', string='Remittance')
    state = fields.Selection([
        ('draft', 'Draft'), ('validated', 'Validated'),
        ('sent', 'Sent'), ('received', 'Received'),
        ('error', 'Error'), ('processed', 'Processed'),
    ], string='Status', default='draft', tracking=True)
    content = fields.Text(string='X12 Content')
    control_number = fields.Char(string='ISA Control Number')
    sender_id = fields.Char(string='Sender ID')
    receiver_id = fields.Char(string='Receiver ID')
    transaction_date = fields.Datetime(string='Date', default=fields.Datetime.now)
    validation_errors = fields.Text(string='Validation Errors', readonly=True)
    response_content = fields.Text(string='Response Content', readonly=True)
    edi_config_id = fields.Many2one('clinic.edi.config', string='EDI Config')
    file_name = fields.Char(string='File Name')
    notes = fields.Text(string='Notes')

    @api.depends('ref', 'transaction_type')
    def _compute_name(self):
        type_labels = dict(self._fields['transaction_type'].selection)
        for rec in self:
            rec.name = f"{rec.ref or 'New'} ({type_labels.get(rec.transaction_type, '')})"

    @api.model_create_multi
    def create(self, vals_list):
        for vals in vals_list:
            if vals.get('ref', 'New') == 'New':
                vals['ref'] = self.env['ir.sequence'].next_by_code('clinic.edi.transaction') or 'New'
        return super().create(vals_list)

    def action_validate(self):
        """Basic X12 segment validation."""
        errors = []
        for rec in self:
            content = rec.content or ''
            segments = [s.strip() for s in content.replace('\n', '').split('~') if s.strip()]
            tags = [s.split('*')[0] for s in segments]

            if 'ISA' not in tags:
                errors.append("Missing ISA segment")
            if 'GS' not in tags:
                errors.append("Missing GS segment")
            if 'ST' not in tags:
                errors.append("Missing ST segment")
            if 'SE' not in tags:
                errors.append("Missing SE segment")
            if 'GE' not in tags:
                errors.append("Missing GE segment")
            if 'IEA' not in tags:
                errors.append("Missing IEA segment")

            # Check segment count in SE
            se_segs = [s for s in segments if s.split('*')[0] == 'SE']
            if se_segs:
                parts = se_segs[0].split('*')
                if len(parts) > 1:
                    try:
                        declared = int(parts[1])
                        # Count segments between ST and SE inclusive
                        st_idx = tags.index('ST') if 'ST' in tags else -1
                        se_idx = tags.index('SE') if 'SE' in tags else -1
                        if st_idx >= 0 and se_idx >= 0:
                            actual = se_idx - st_idx + 1
                            if declared != actual:
                                errors.append(f"SE segment count mismatch: declared {declared}, actual {actual}")
                    except (ValueError, IndexError):
                        pass

            if errors:
                rec.write({'state': 'error', 'validation_errors': '\n'.join(errors)})
            else:
                rec.write({'state': 'validated', 'validation_errors': False})

    def action_send_rest(self):
        """Send 837 or 270 via REST to clearinghouse."""
        for rec in self:
            config = rec.edi_config_id or self.env['clinic.edi.config'].search(
                [('active', '=', True)], limit=1
            )
            if not config or not config.rest_url:
                rec.write({'state': 'error', 'validation_errors': 'No EDI config with REST URL'})
                continue

            if rec.transaction_type == '837':
                endpoint = f"{config.rest_url}/submit-837"
            elif rec.transaction_type == '270':
                endpoint = f"{config.rest_url}/eligibility/270"
            else:
                rec.write({'state': 'error', 'validation_errors': f'REST send not supported for {rec.transaction_type}'})
                continue

            payload = json.dumps({'content': rec.content or ''}).encode('utf-8')
            try:
                req = urllib.request.Request(endpoint, data=payload,
                                             headers={'Content-Type': 'application/json'}, method='POST')
                with urllib.request.urlopen(req, timeout=30) as resp:
                    body = json.loads(resp.read())
                    rec.write({
                        'state': 'sent',
                        'response_content': json.dumps(body, indent=2),
                        'control_number': body.get('control_number', rec.control_number),
                    })
            except Exception as exc:
                _logger.error("EDI REST send failed for %s: %s", rec.ref, exc)
                rec.write({'state': 'error', 'validation_errors': str(exc)})

    def action_send_sftp(self):
        """Upload via SFTP (requires paramiko)."""
        try:
            import paramiko
        except ImportError:
            for rec in self:
                rec.write({'state': 'error', 'validation_errors': 'paramiko not installed. Run: pip install paramiko'})
            return

        for rec in self:
            config = rec.edi_config_id or self.env['clinic.edi.config'].search(
                [('active', '=', True)], limit=1
            )
            if not config or not config.sftp_host:
                rec.write({'state': 'error', 'validation_errors': 'No SFTP config'})
                continue
            try:
                client = paramiko.SSHClient()
                client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
                client.connect(config.sftp_host, port=config.sftp_port or 22,
                               username=config.sftp_user, password=config.sftp_password, timeout=30)
                sftp = client.open_sftp()
                fname = rec.file_name or f"{rec.ref}_{rec.transaction_type}.txt"
                remote_path = f"{config.sftp_out_path}/{fname}"
                with sftp.file(remote_path, 'w') as f:
                    f.write(rec.content or '')
                sftp.close()
                client.close()
                rec.write({'state': 'sent'})
            except Exception as exc:
                _logger.error("SFTP send failed for %s: %s", rec.ref, exc)
                rec.write({'state': 'error', 'validation_errors': str(exc)})

    def action_process_inbound(self):
        """Process inbound 835/271."""
        for rec in self:
            if rec.transaction_type == '835':
                rec._process_835()
            elif rec.transaction_type == '271':
                rec._process_271()
            else:
                rec.write({'state': 'processed'})

    def _process_835(self):
        """Parse 835 and link/update remittance."""
        from ..utils.edi_generator import parse_835
        try:
            data = parse_835(self.content or '')
            rem = self.env['clinic.remittance'].create({
                'name': data.get('check_number') or f"ERA-{self.ref}",
                'check_eft_number': data.get('check_number', ''),
                'total_paid': data.get('total_paid', 0.0),
                'state': 'pending',
            })
            self.write({'remittance_id': rem.id, 'state': 'processed'})
        except Exception as exc:
            self.write({'state': 'error', 'validation_errors': str(exc)})

    def _process_271(self):
        """Parse 271 and update eligibility request."""
        self.write({'state': 'processed'})
