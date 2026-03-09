import time
import logging
from odoo import api, fields, models

_logger = logging.getLogger(__name__)


class ClinicAutomationConfig(models.Model):
    _name = 'clinic.automation.config'
    _description = 'Clinic Automation Configuration'
    _rec_name = 'company_id'

    company_id = fields.Many2one('res.company', string='Company', required=True,
                                 default=lambda self: self.env.company)
    active = fields.Boolean(default=True)

    # Appointment reminders
    reminder_enabled = fields.Boolean(string='Appointment Reminders', default=True)
    reminder_hours_before = fields.Integer(string='Hours Before Appointment', default=24)

    # Stock alerts
    stock_alert_enabled = fields.Boolean(string='Stock Alerts', default=True)
    stock_min_qty = fields.Float(string='Default Min Quantity', default=5.0)

    # Expiry alerts
    expiry_alert_enabled = fields.Boolean(string='Expiry Alerts', default=True)
    expiry_days_warning = fields.Integer(string='Days Before Expiry to Warn', default=30)

    # EDI jobs
    edi_auto_send = fields.Boolean(string='Auto-send EDI Claims', default=False)
    edi_auto_import = fields.Boolean(string='Auto-import EDI Remittances', default=False)

    _sql_constraints = [
        ('company_unique', 'unique(company_id)', 'Only one automation config per company.'),
    ]

    def _log(self, job_type, result, message, records=0, duration_ms=0):
        self.env['clinic.automation.log'].create({
            'job_type': job_type,
            'company_id': self.env.company.id,
            'result': result,
            'message': message,
            'records_processed': records,
            'duration_ms': duration_ms,
        })

    @api.model
    def cron_send_appointment_reminders(self):
        t0 = time.time()
        configs = self.search([('reminder_enabled', '=', True)])
        total = 0
        for cfg in configs:
            Appointment = self.env.get('clinic.appointment')
            if not Appointment:
                continue
            try:
                from datetime import datetime, timedelta
                now = datetime.now()
                cutoff = now + timedelta(hours=cfg.reminder_hours_before)
                appointments = Appointment.search([
                    ('state', '=', 'confirmed'),
                    ('appointment_date', '>=', now),
                    ('appointment_date', '<=', cutoff),
                    ('company_id', '=', cfg.company_id.id),
                ])
                for appt in appointments:
                    appt.message_post(
                        body=f'Reminder: appointment on {appt.appointment_date}',
                        message_type='notification',
                    )
                total += len(appointments)
            except Exception as e:
                _logger.error('Appointment reminder error (company %s): %s', cfg.company_id.name, e)
                cfg._log('appointment_reminder', 'error', str(e))
                continue
        duration = int((time.time() - t0) * 1000)
        if configs:
            configs[0]._log('appointment_reminder', 'success',
                            f'Sent {total} reminders', total, duration)
        _logger.info('cron_send_appointment_reminders: %d reminders sent', total)

    @api.model
    def cron_check_stock_alerts(self):
        t0 = time.time()
        configs = self.search([('stock_alert_enabled', '=', True)])
        total = 0
        for cfg in configs:
            StockQuant = self.env.get('stock.quant')
            if not StockQuant:
                continue
            try:
                quants = StockQuant.search([
                    ('quantity', '<=', cfg.stock_min_qty),
                    ('location_id.usage', '=', 'internal'),
                    ('company_id', '=', cfg.company_id.id),
                ])
                for quant in quants:
                    _logger.warning(
                        'Low stock: %s qty=%.2f (min=%.2f) [%s]',
                        quant.product_id.name, quant.quantity,
                        cfg.stock_min_qty, cfg.company_id.name,
                    )
                total += len(quants)
            except Exception as e:
                _logger.error('Stock alert error (company %s): %s', cfg.company_id.name, e)
                cfg._log('stock_alert', 'error', str(e))
                continue
        duration = int((time.time() - t0) * 1000)
        if configs:
            configs[0]._log('stock_alert', 'success',
                            f'Checked {total} low-stock items', total, duration)
        _logger.info('cron_check_stock_alerts: %d items below minimum', total)

    @api.model
    def cron_check_expiry_alerts(self):
        t0 = time.time()
        configs = self.search([('expiry_alert_enabled', '=', True)])
        total = 0
        for cfg in configs:
            StockLot = self.env.get('stock.lot')
            if not StockLot:
                continue
            try:
                from datetime import date, timedelta
                threshold = date.today() + timedelta(days=cfg.expiry_days_warning)
                lots = StockLot.search([
                    ('expiration_date', '!=', False),
                    ('expiration_date', '<=', threshold),
                    ('company_id', '=', cfg.company_id.id),
                ])
                for lot in lots:
                    _logger.warning(
                        'Expiry alert: %s lot=%s expires=%s [%s]',
                        lot.product_id.name, lot.name,
                        lot.expiration_date, cfg.company_id.name,
                    )
                total += len(lots)
            except Exception as e:
                _logger.error('Expiry alert error (company %s): %s', cfg.company_id.name, e)
                cfg._log('expiry_alert', 'error', str(e))
                continue
        duration = int((time.time() - t0) * 1000)
        if configs:
            configs[0]._log('expiry_alert', 'success',
                            f'Checked {total} expiring lots', total, duration)
        _logger.info('cron_check_expiry_alerts: %d lots expiring soon', total)

    @api.model
    def cron_run_edi_jobs(self):
        t0 = time.time()
        configs = self.search([
            '|', ('edi_auto_send', '=', True), ('edi_auto_import', '=', True)
        ])
        sent = imported = 0
        EdiTransaction = self.env.get('clinic.edi.transaction')
        if not EdiTransaction:
            _logger.info('cron_run_edi_jobs: clinic.edi.transaction not available')
            return
        for cfg in configs:
            try:
                if cfg.edi_auto_send:
                    drafts = EdiTransaction.search([
                        ('state', '=', 'validated'),
                        ('direction', '=', 'outbound'),
                        ('company_id', '=', cfg.company_id.id),
                    ])
                    for tx in drafts:
                        try:
                            tx.action_send_rest()
                            sent += 1
                        except Exception as e:
                            _logger.error('EDI send error tx=%d: %s', tx.id, e)
                if cfg.edi_auto_import:
                    received = EdiTransaction.search([
                        ('state', '=', 'received'),
                        ('direction', '=', 'inbound'),
                        ('company_id', '=', cfg.company_id.id),
                    ])
                    for tx in received:
                        try:
                            tx.action_process_inbound()
                            imported += 1
                        except Exception as e:
                            _logger.error('EDI import error tx=%d: %s', tx.id, e)
            except Exception as e:
                _logger.error('EDI job error (company %s): %s', cfg.company_id.name, e)
                cfg._log('edi_send', 'error', str(e))
        duration = int((time.time() - t0) * 1000)
        if configs:
            configs[0]._log('edi_send', 'success',
                            f'Sent {sent}, imported {imported}', sent + imported, duration)
        _logger.info('cron_run_edi_jobs: sent=%d imported=%d', sent, imported)
