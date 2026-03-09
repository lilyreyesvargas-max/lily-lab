from odoo.tests.common import TransactionCase


class TestClinicInventoryInternal(TransactionCase):

    def test_supply_request_sequence(self):
        req = self.env['clinic.supply.request'].create({'company_id': self.env.company.id})
        self.assertIn('SREQ', req.name)

    def test_supply_request_workflow(self):
        req = self.env['clinic.supply.request'].create({'company_id': self.env.company.id})
        self.assertEqual(req.state, 'draft')
        req.action_submit()
        self.assertEqual(req.state, 'submitted')
        req.action_approve()
        self.assertEqual(req.state, 'approved')
        self.assertEqual(req.approved_by.id, self.env.uid)
