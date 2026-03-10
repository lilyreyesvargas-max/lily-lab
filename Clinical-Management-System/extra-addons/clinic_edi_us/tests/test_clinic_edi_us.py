from odoo.tests.common import TransactionCase, tagged


@tagged('post_install', '-at_install')
class TestClinicEdiUs(TransactionCase):

    def test_837_generation(self):
        from ..utils.edi_generator import generate_270
        patient = self.env['clinic.patient'].create({
            'name': 'EDI Patient', 'date_of_birth': '1980-01-01', 'gender': 'male'
        })
        insurer = self.env['clinic.insurer'].create({'name': 'EDI Insurer', 'payer_id': 'EDI001'})
        er = self.env['clinic.edi.eligibility.request'].create({
            'patient_id': patient.id, 'insurer_id': insurer.id
        })
        config = self.env['clinic.edi.config'].create({
            'name': 'Test Config', 'rest_url': 'http://localhost:18080'
        })
        content = generate_270(er, config)
        self.assertIn('ISA', content)
        self.assertIn('ST*270', content)
        self.assertIn('IEA', content)

    def test_835_parsing(self):
        from ..utils.edi_generator import parse_835
        content = """ISA*00*          *00*          *ZZ*CLRHOUSE       *ZZ*SENDCLINIC     *240102*0900*^*00501*000000003*0*T*:~
GS*HP*CLRHOUSE*SENDCLINIC*20240102*0900*3*X*005010X221A1~
ST*835*0003~
BPR*I*500.00*C*ACH~
TRN*1*CHECK12345*1512345678~
N1*PR*BLUE CROSS PPO*XV*BCBS001~
N1*PE*MAIN CLINIC*XX*1234567890~
CLP*CLAIM001*1*500.00*500.00**MC*ICN001*11~
SVC*HC:99213*150.00*150.00**1~
SE*10*0003~
GE*1*3~
IEA*1*000000003~"""
        result = parse_835(content)
        self.assertEqual(result['check_number'], 'CHECK12345')
        self.assertEqual(result['total_paid'], 500.0)
        self.assertEqual(len(result['claims']), 1)
        self.assertEqual(result['claims'][0]['claim_ref'], 'CLAIM001')

    def test_271_parsing(self):
        from ..utils.edi_generator import parse_271
        content = """ISA*00*          *00*          *ZZ*CLRHOUSE       *ZZ*SENDCLINIC     *240101*1201*^*00501*000000005*0*T*:~
ST*271*0005*005010X279A1~
NM1*IL*1*DOE*JOHN****MI*INS123456~
EB*1*FAM*30*CI*PPO GOLD PLAN~
EB*C*FAM**CI**23*1500.00~
SE*5*0005~"""
        result = parse_271(content)
        self.assertTrue(result['eligible'])
        self.assertEqual(result['plan_name'], 'PPO GOLD PLAN')

    def test_transaction_validation_valid(self):
        tx = self.env['clinic.edi.transaction'].create({
            'transaction_type': '837',
            'direction': 'outbound',
            'content': """ISA*00*          *00*          *ZZ*SENDCLINIC     *ZZ*CLRHOUSE       *240101*1200*^*00501*000000001*0*T*:~
GS*HC*SENDCLINIC*CLRHOUSE*20240101*1200*1*X*005010X222A1~
ST*837*0001~
CLM*TEST001*100.00~
SE*2*0001~
GE*1*1~
IEA*1*000000001~"""
        })
        tx.action_validate()
        self.assertEqual(tx.state, 'validated')

    def test_transaction_validation_invalid(self):
        tx = self.env['clinic.edi.transaction'].create({
            'transaction_type': '837',
            'direction': 'outbound',
            'content': 'CLM*BAD*0.00~',
        })
        tx.action_validate()
        self.assertEqual(tx.state, 'error')
        self.assertIn('Missing ISA', tx.validation_errors)

    # ── Record-Level Security (multi-company isolation) ──────────────────────

    def _make_rls_context(self):
        company_s1 = self.env['res.company'].create({'name': 'EDI Branch S1'})
        company_s2 = self.env['res.company'].create({'name': 'EDI Branch S2'})
        billing_group = self.env.ref('clinic_core.clinic_group_billing')
        billing_user_s1 = self.env['res.users'].create({
            'name': 'EDI Billing S1',
            'login': 'edi_billing_s1@test.com',
            'company_id': company_s1.id,
            'company_ids': [(4, company_s1.id)],
            'groups_id': [(4, billing_group.id)],
        })
        return company_s1, company_s2, billing_user_s1

    def test_edi_user_cannot_see_other_company_transaction(self):
        """RLS: billing user at S1 must not see EDI transactions from S2."""
        company_s1, company_s2, billing_user_s1 = self._make_rls_context()
        self.env['clinic.edi.transaction'].sudo().create({
            'transaction_type': '837',
            'direction': 'outbound',
            'company_id': company_s2.id,
            'content': 'ISA~',
        })
        visible = self.env['clinic.edi.transaction'].with_user(billing_user_s1).search([])
        for tx in visible:
            self.assertNotEqual(
                tx.company_id.id, company_s2.id,
                "Billing user S1 must not see EDI transactions from Branch S2"
            )

    def test_edi_user_can_see_own_company_transaction(self):
        """RLS: billing user at S1 must see EDI transactions from S1."""
        company_s1, company_s2, billing_user_s1 = self._make_rls_context()
        tx_s1 = self.env['clinic.edi.transaction'].sudo().create({
            'transaction_type': '837',
            'direction': 'outbound',
            'company_id': company_s1.id,
            'content': 'ISA~',
        })
        visible = self.env['clinic.edi.transaction'].with_user(billing_user_s1).search([])
        self.assertIn(tx_s1, visible, "Billing user S1 must see EDI transactions from Branch S1")
