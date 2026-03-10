from odoo.tests.common import TransactionCase, tagged


@tagged('post_install', '-at_install')
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

    # ── Record-Level Security (multi-company isolation) ──────────────────────

    def _make_rls_context(self):
        company_s1 = self.env['res.company'].create({'name': 'Inv Branch S1'})
        company_s2 = self.env['res.company'].create({'name': 'Inv Branch S2'})
        nurse_group = self.env.ref('clinic_core.clinic_group_nurse')
        nurse_s1 = self.env['res.users'].create({
            'name': 'Inv Nurse S1',
            'login': 'inv_nurse_s1@test.com',
            'company_id': company_s1.id,
            'company_ids': [(4, company_s1.id)],
            'groups_id': [(4, nurse_group.id)],
        })
        return company_s1, company_s2, nurse_s1

    def test_nurse_cannot_see_other_company_supply_request(self):
        """RLS: nurse at S1 must not see supply requests from S2."""
        company_s1, company_s2, nurse_s1 = self._make_rls_context()
        self.env['clinic.supply.request'].sudo().create({'company_id': company_s2.id})
        visible = self.env['clinic.supply.request'].with_user(nurse_s1).search([])
        for req in visible:
            self.assertNotEqual(
                req.company_id.id, company_s2.id,
                "Nurse S1 must not see supply requests from Branch S2"
            )

    def test_nurse_can_see_own_company_supply_request(self):
        """RLS: nurse at S1 must see supply requests from S1."""
        company_s1, company_s2, nurse_s1 = self._make_rls_context()
        req_s1 = self.env['clinic.supply.request'].sudo().create(
            {'company_id': company_s1.id}
        )
        visible = self.env['clinic.supply.request'].with_user(nurse_s1).search([])
        self.assertIn(req_s1, visible, "Nurse S1 must see supply requests from Branch S1")
