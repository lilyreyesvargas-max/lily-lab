from odoo.tests.common import TransactionCase


class TestClinicAutomationConfig(TransactionCase):

    def setUp(self):
        super().setUp()
        self.Config = self.env['clinic.automation.config']
        self.Log = self.env['clinic.automation.log']

    def test_create_config(self):
        cfg = self.Config.create({
            'company_id': self.env.company.id,
            'reminder_enabled': True,
            'reminder_hours_before': 24,
        })
        self.assertTrue(cfg.active)
        self.assertEqual(cfg.reminder_hours_before, 24)

    def test_config_company_unique(self):
        self.Config.create({'company_id': self.env.company.id})
        with self.assertRaises(Exception):
            self.Config.create({'company_id': self.env.company.id})

    def test_cron_appointment_reminders_no_crash(self):
        self.Config.create({
            'company_id': self.env.company.id,
            'reminder_enabled': True,
        })
        # Should not crash even without clinic.appointment model installed
        self.Config.cron_send_appointment_reminders()

    def test_cron_stock_alerts_no_crash(self):
        self.Config.create({
            'company_id': self.env.company.id,
            'stock_alert_enabled': True,
        })
        self.Config.cron_check_stock_alerts()

    def test_cron_expiry_alerts_no_crash(self):
        self.Config.create({
            'company_id': self.env.company.id,
            'expiry_alert_enabled': True,
        })
        self.Config.cron_check_expiry_alerts()

    def test_cron_edi_jobs_no_crash(self):
        self.Config.create({
            'company_id': self.env.company.id,
            'edi_auto_send': True,
        })
        self.Config.cron_run_edi_jobs()


class TestClinicAutomationLog(TransactionCase):

    def setUp(self):
        super().setUp()
        self.Log = self.env['clinic.automation.log']

    def test_create_log(self):
        log = self.Log.create({
            'job_type': 'appointment_reminder',
            'result': 'success',
            'message': 'Sent 5 reminders',
            'records_processed': 5,
            'duration_ms': 120,
        })
        self.assertEqual(log.result, 'success')
        self.assertEqual(log.records_processed, 5)

    def test_log_error(self):
        log = self.Log.create({
            'job_type': 'edi_send',
            'result': 'error',
            'message': 'Connection refused',
        })
        self.assertEqual(log.result, 'error')

    def test_log_default_result(self):
        log = self.Log.create({'job_type': 'stock_alert'})
        self.assertEqual(log.result, 'success')
