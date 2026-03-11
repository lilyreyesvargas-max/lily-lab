#!/usr/bin/env python3
"""
Comprehensive audit and improvement of ALL demo data in Clinical Management System.
Date reference: 2026-03-11
"""

import xmlrpc.client
import subprocess
import json
from datetime import date, datetime, timedelta
from pprint import pprint

# ─── Connection ──────────────────────────────────────────────────────────────
URL = "http://localhost:8070"
DB  = "odoo_clinic"
USER = "admin@clinic.local"
PWD  = "admin_local_strong"

common = xmlrpc.client.ServerProxy(f"{URL}/xmlrpc/2/common")
UID = common.authenticate(DB, USER, PWD, {})
models = xmlrpc.client.ServerProxy(f"{URL}/xmlrpc/2/object")

def call(model, method, *args, **kw):
    return models.execute_kw(DB, UID, PWD, model, method, list(args), kw)

def search_read(model, domain=None, fields=None, limit=None):
    domain = domain or []
    kw = {}
    if fields:
        kw['fields'] = fields
    if limit:
        kw['limit'] = limit
    return call(model, 'search_read', domain, **kw)

def psql(sql):
    result = subprocess.run(
        ["docker", "exec", "clinic_db", "psql", "-U", "odoo", "-d", "odoo_clinic", "-c", sql],
        capture_output=True, text=True
    )
    if result.returncode != 0:
        print(f"  PSQL ERROR: {result.stderr}")
    else:
        print(f"  PSQL OK: {result.stdout.strip()}")
    return result

def count(model, domain=None):
    return call(model, 'search_count', domain or [])

# ─── Summary tracking ─────────────────────────────────────────────────────────
summary = {}

def report(model, fixed=0, created=0, final=None):
    summary[model] = {'fixed': fixed, 'created': created, 'final': final or count(model)}

print("=" * 70)
print("CLINICAL MANAGEMENT SYSTEM — FULL DEMO AUDIT & IMPROVEMENT")
print(f"Date: 2026-03-11  |  Odoo UID: {UID}")
print("=" * 70)

# ─── PATIENTS ─────────────────────────────────────────────────────────────────
print("\n\n### AUDITING: clinic.patient ###")
patients = search_read('clinic.patient',
    fields=['id','name','company_id','date_of_birth','gender','phone','email',
            'active','blood_type','emergency_contact_name'])
print(f"  Total patients: {len(patients)}")
bad_patients = [p for p in patients if p.get('company_id') and p['company_id'][0] == 1]
print(f"  Patients with company_id=1 (bad): {len(bad_patients)}")
for p in bad_patients:
    print(f"    Fixing patient {p['id']}: {p['name']}")
    psql(f"UPDATE clinic_patient SET company_id=3 WHERE id={p['id']};")

# Check patients missing key fields
for p in patients:
    missing = []
    if not p.get('date_of_birth'): missing.append('date_of_birth')
    if not p.get('gender'): missing.append('gender')
    if not p.get('phone'): missing.append('phone')
    if missing:
        print(f"  Patient {p['id']} {p['name']} missing: {missing}")

report('clinic.patient', fixed=len(bad_patients))

# ─── PHYSICIANS ───────────────────────────────────────────────────────────────
print("\n\n### AUDITING: clinic.physician ###")
try:
    physicians = search_read('clinic.physician',
        fields=['id','name','company_id','specialty_id','user_id','license_number','active'])
    print(f"  Total physicians: {len(physicians)}")
    bad_phys = [p for p in physicians if p.get('company_id') and p['company_id'][0] == 1]
    print(f"  Physicians with company_id=1: {len(bad_phys)}")
    for p in bad_phys:
        print(f"    Fixing physician {p['id']}: {p['name']}")
        psql(f"UPDATE clinic_physician SET company_id=3 WHERE id={p['id']};")
    report('clinic.physician', fixed=len(bad_phys))
except Exception as e:
    print(f"  ERROR: {e}")

# ─── INSURERS ─────────────────────────────────────────────────────────────────
print("\n\n### AUDITING: clinic.insurer ###")
try:
    insurers = search_read('clinic.insurer',
        fields=['id','name','company_id','payer_id','active','address'])
    print(f"  Total insurers: {len(insurers)}")
    for ins in insurers:
        print(f"  Insurer {ins['id']}: {ins['name']} company={ins.get('company_id')}")
    bad_ins = [i for i in insurers if i.get('company_id') and i['company_id'][0] == 1]
    for i in bad_ins:
        psql(f"UPDATE clinic_insurer SET company_id=3 WHERE id={i['id']};")
    report('clinic.insurer', fixed=len(bad_ins))
except Exception as e:
    print(f"  ERROR: {e}")

# ─── INSURER PLANS ────────────────────────────────────────────────────────────
print("\n\n### AUDITING: clinic.insurer.plan ###")
try:
    plans = search_read('clinic.insurer.plan',
        fields=['id','name','insurer_id','company_id','plan_type','copay','deductible',
                'active','group_number'])
    print(f"  Total plans: {len(plans)}")
    for pl in plans:
        print(f"  Plan {pl['id']}: {pl['name']} insurer={pl.get('insurer_id')} company={pl.get('company_id')}")

    bad_plans = [pl for pl in plans if pl.get('company_id') and pl['company_id'][0] == 1]
    for pl in bad_plans:
        psql(f"UPDATE clinic_insurer_plan SET company_id=3 WHERE id={pl['id']};")

    # Get insurer IDs for new plans
    insurer_ids = list({pl['insurer_id'][0] for pl in plans if pl.get('insurer_id')})
    print(f"  Insurer IDs found: {insurer_ids}")

    # Check plan types available
    plan_fields = call('clinic.insurer.plan', 'fields_get', ['plan_type'], {'attributes': ['selection']})
    plan_types = [s[0] for s in plan_fields.get('plan_type', {}).get('selection', [('hmo','HMO')])]
    print(f"  Available plan types: {plan_types}")

    created_plans = 0
    if insurer_ids:
        existing_names = {pl['name'] for pl in plans}
        new_plans = []
        plan_defs = [
            {'name': 'Gold PPO Plan', 'plan_type': 'ppo' if 'ppo' in plan_types else plan_types[0],
             'copay': 30.0, 'deductible': 500.0, 'group_number': 'GLD-PPO-001'},
            {'name': 'Silver HMO Plan', 'plan_type': 'hmo' if 'hmo' in plan_types else plan_types[0],
             'copay': 20.0, 'deductible': 1000.0, 'group_number': 'SLV-HMO-001'},
            {'name': 'Platinum PPO Plus', 'plan_type': 'ppo' if 'ppo' in plan_types else plan_types[0],
             'copay': 15.0, 'deductible': 250.0, 'group_number': 'PLT-PPO-002'},
            {'name': 'Bronze HDH Plan', 'plan_type': 'hdhp' if 'hdhp' in plan_types else plan_types[-1],
             'copay': 50.0, 'deductible': 3000.0, 'group_number': 'BRZ-HDH-001'},
            {'name': 'Medicare Advantage A', 'plan_type': 'medicare' if 'medicare' in plan_types else plan_types[0],
             'copay': 10.0, 'deductible': 0.0, 'group_number': 'MCA-ADV-001'},
            {'name': 'Medicaid Standard', 'plan_type': 'medicaid' if 'medicaid' in plan_types else plan_types[0],
             'copay': 5.0, 'deductible': 0.0, 'group_number': 'MCD-STD-001'},
        ]
        for idx, pd in enumerate(plan_defs):
            if pd['name'] not in existing_names:
                ins_id = insurer_ids[idx % len(insurer_ids)]
                pd['insurer_id'] = ins_id
                pd['company_id'] = 3
                pd['active'] = True
                try:
                    new_id = call('clinic.insurer.plan', 'create', pd)
                    print(f"  Created plan '{pd['name']}' id={new_id}")
                    created_plans += 1
                except Exception as e2:
                    print(f"  Could not create plan '{pd['name']}': {e2}")

    report('clinic.insurer.plan', fixed=len(bad_plans), created=created_plans)
except Exception as e:
    print(f"  ERROR: {e}")

# ─── PATIENT POLICIES ─────────────────────────────────────────────────────────
print("\n\n### AUDITING: clinic.patient.policy ###")
try:
    policies = search_read('clinic.patient.policy',
        fields=['id','name','patient_id','insurer_id','plan_id','company_id',
                'policy_number','start_date','end_date','state','is_primary',
                'subscriber_id','group_number'])
    print(f"  Total policies: {len(policies)}")

    bad_pol = [p for p in policies if p.get('company_id') and p['company_id'][0] == 1]
    print(f"  Policies with company_id=1: {len(bad_pol)}")
    for p in bad_pol:
        psql(f"UPDATE clinic_patient_policy SET company_id=3 WHERE id={p['id']};")

    # Check for expired dates (2024)
    old_pol = [p for p in policies if p.get('end_date') and p['end_date'] < '2025-01-01']
    print(f"  Policies with end_date in 2024 or earlier: {len(old_pol)}")
    for p in old_pol:
        print(f"    Fixing policy {p['id']} end_date={p['end_date']}")
        call('clinic.patient.policy', 'write', [p['id']], {'end_date': '2026-12-31'})

    # Get patient and plan IDs for new policies
    all_patients = search_read('clinic.patient', fields=['id','name','company_id'])
    all_plans = search_read('clinic.insurer.plan', fields=['id','name','insurer_id','company_id'])

    # Map company to patients
    company_patients = {}
    for pt in all_patients:
        cid = pt.get('company_id', [3])[0] if pt.get('company_id') else 3
        company_patients.setdefault(cid, []).append(pt['id'])

    existing_patient_ids = {p['patient_id'][0] for p in policies if p.get('patient_id')}

    # Create policies for patients without one
    created_pol = 0
    pol_counter = 900
    for pt in all_patients:
        if pt['id'] not in existing_patient_ids and all_plans:
            plan = all_plans[pt['id'] % len(all_plans)]
            pol_counter += 1
            try:
                new_id = call('clinic.patient.policy', 'create', {
                    'patient_id': pt['id'],
                    'insurer_id': plan['insurer_id'][0],
                    'plan_id': plan['id'],
                    'company_id': pt.get('company_id', [3])[0] if pt.get('company_id') else 3,
                    'policy_number': f"POL-{pol_counter:04d}",
                    'start_date': '2025-01-01',
                    'end_date': '2026-12-31',
                    'state': 'active',
                    'is_primary': True,
                    'subscriber_id': f"SUB-{pol_counter:04d}",
                    'group_number': f"GRP-{pol_counter:04d}",
                })
                created_pol += 1
                if created_pol <= 5:
                    print(f"  Created policy id={new_id} for patient {pt['name']}")
            except Exception as e2:
                pass  # Skip if already exists or other error

    print(f"  Created {created_pol} new policies")
    report('clinic.patient.policy', fixed=len(bad_pol)+len(old_pol), created=created_pol)
except Exception as e:
    print(f"  ERROR: {e}")

# ─── EHR ENCOUNTERS ────────────────────────────────────────────────────────────
print("\n\n### AUDITING: clinic.ehr.encounter ###")
try:
    enc_fields = call('clinic.ehr.encounter', 'fields_get', [],
                      {'attributes': ['string', 'type', 'selection', 'required']})
    state_sel = enc_fields.get('state', {}).get('selection', [])
    print(f"  Available states: {state_sel}")
    states = [s[0] for s in state_sel] if state_sel else ['draft','in_progress','done','cancelled']

    encounters = search_read('clinic.ehr.encounter',
        fields=['id','name','patient_id','physician_id','specialty_id','company_id',
                'state','encounter_date','chief_complaint','diagnosis_code','notes',
                'appointment_id'])
    print(f"  Total encounters: {len(encounters)}")

    bad_enc = [e for e in encounters if e.get('company_id') and e['company_id'][0] == 1]
    print(f"  Encounters with company_id=1: {len(bad_enc)}")
    for e in bad_enc:
        psql(f"UPDATE clinic_ehr_encounter SET company_id=3 WHERE id={e['id']};")

    old_enc = [e for e in encounters if e.get('encounter_date') and str(e['encounter_date'])[:4] == '2024']
    print(f"  Encounters with 2024 dates: {len(old_enc)}")

    missing_physician = [e for e in encounters if not e.get('physician_id')]
    print(f"  Encounters missing physician: {len(missing_physician)}")
    missing_specialty = [e for e in encounters if not e.get('specialty_id')]
    print(f"  Encounters missing specialty: {len(missing_specialty)}")

    # Get all physicians
    all_physicians = search_read('clinic.physician', fields=['id','name','company_id','specialty_id','user_id'])
    all_patients_enc = search_read('clinic.patient', fields=['id','name','company_id'])

    # Map company to physician
    company_physicians = {}
    for ph in all_physicians:
        cid = ph.get('company_id', [3])[0] if ph.get('company_id') else 3
        company_physicians.setdefault(cid, []).append(ph)

    # Fix missing fields on existing encounters
    fixed_enc = len(bad_enc)
    for e in encounters:
        updates = {}
        cid = e.get('company_id', [3])[0] if e.get('company_id') else 3
        if cid == 1:
            cid = 3

        if not e.get('physician_id'):
            phys_list = company_physicians.get(cid, company_physicians.get(3, []))
            if phys_list:
                ph = phys_list[e['id'] % len(phys_list)]
                updates['physician_id'] = ph['id']
                if ph.get('specialty_id'):
                    updates['specialty_id'] = ph['specialty_id'][0]

        if not e.get('specialty_id') and e.get('physician_id'):
            pass  # Will be set from physician

        if not e.get('chief_complaint'):
            complaints = ['Headache and dizziness', 'Lower back pain', 'Fatigue and weakness',
                          'Chest discomfort', 'Abdominal pain', 'Shortness of breath',
                          'Joint pain', 'Sore throat and fever', 'Skin rash', 'Vision problems']
            updates['chief_complaint'] = complaints[e['id'] % len(complaints)]

        if not e.get('notes'):
            updates['notes'] = 'Patient presented with reported symptoms. Examination conducted. Treatment plan discussed.'

        if updates:
            try:
                call('clinic.ehr.encounter', 'write', [e['id']], updates)
                fixed_enc += 1
            except Exception as e2:
                print(f"  Could not fix encounter {e['id']}: {e2}")

    # Create new encounters with diverse states and data
    created_enc = 0
    encounter_templates = [
        # (company_id, state, encounter_date, chief_complaint, specialty_idx)
        (3, 'draft', '2026-03-11', 'Annual wellness checkup', 0),
        (3, 'in_progress', '2026-03-11', 'Follow-up hypertension', 0),
        (3, 'done', '2026-03-10', 'Prenatal visit 28 weeks', 1),
        (3, 'done', '2026-03-09', 'Visual acuity decline', 2),
        (3, 'cancelled', '2026-03-08', 'Dental pain upper molar', 3),
        (3, 'done', '2026-03-07', 'Persistent cough and fever', 0),
        (3, 'done', '2026-03-05', 'Postpartum 6-week checkup', 1),
        (4, 'done', '2026-03-10', 'Routine physical exam', 0),
        (4, 'done', '2026-03-09', 'Knee pain and swelling', 0),
        (4, 'in_progress', '2026-03-11', 'Pap smear and exam', 1),
        (5, 'done', '2026-03-10', 'Cataract consultation', 2),
        (5, 'done', '2026-03-08', 'Glaucoma follow-up', 2),
        (5, 'draft', '2026-03-11', 'Eye pressure check', 2),
        (6, 'done', '2026-03-09', 'Teeth cleaning and exam', 3),
        (6, 'done', '2026-03-07', 'Root canal evaluation', 3),
        (6, 'in_progress', '2026-03-11', 'Orthodontic consultation', 3),
        (3, 'done', '2026-02-28', 'Diabetes management review', 0),
        (3, 'done', '2026-02-25', 'Blood pressure control', 0),
        (4, 'done', '2026-02-20', 'Urinary tract infection', 0),
        (5, 'done', '2026-02-15', 'Contact lens fitting', 2),
    ]

    specialty_map = {0: 1, 1: 2, 2: 3, 3: 4}  # index → specialty_id

    existing_enc_count = len(encounters)
    for idx, (cid, st, dt, complaint, spec_idx) in enumerate(encounter_templates):
        phys_list = company_physicians.get(cid, company_physicians.get(3, []))
        if not phys_list:
            continue
        ph = phys_list[idx % len(phys_list)]
        pat_list = [p for p in all_patients_enc if (p.get('company_id', [cid])[0] if p.get('company_id') else cid) == cid]
        if not pat_list:
            pat_list = all_patients_enc

        pat = pat_list[idx % len(pat_list)]
        spec_id = specialty_map.get(spec_idx, 1)
        if ph.get('specialty_id'):
            spec_id = ph['specialty_id'][0]

        enc_data = {
            'patient_id': pat['id'],
            'physician_id': ph['id'],
            'specialty_id': spec_id,
            'company_id': cid,
            'state': st,
            'encounter_date': dt,
            'chief_complaint': complaint,
            'notes': f'Patient visit: {complaint}. Clinical examination performed. Plan of care established.',
        }
        try:
            new_id = call('clinic.ehr.encounter', 'create', enc_data)
            created_enc += 1
            if created_enc <= 5:
                print(f"  Created encounter id={new_id} state={st} cid={cid}")
        except Exception as e2:
            print(f"  Could not create encounter idx={idx}: {e2}")

    print(f"  Created {created_enc} new encounters")
    report('clinic.ehr.encounter', fixed=fixed_enc, created=created_enc)
except Exception as e:
    print(f"  ERROR: {e}")
    import traceback; traceback.print_exc()

# ─── BILLING CLAIMS ───────────────────────────────────────────────────────────
print("\n\n### AUDITING: clinic.billing.claim ###")
try:
    claim_fields = call('clinic.billing.claim', 'fields_get', [],
                        {'attributes': ['string', 'type', 'selection']})
    state_sel = claim_fields.get('state', {}).get('selection', [])
    print(f"  Available states: {state_sel}")
    states = [s[0] for s in state_sel] if state_sel else ['draft','submitted','paid','denied']

    claims = search_read('clinic.billing.claim',
        fields=['id','name','patient_id','encounter_id','insurer_id','company_id',
                'state','date_service','amount_billed','amount_paid','claim_number'])
    print(f"  Total claims: {len(claims)}")

    bad_claims = [c for c in claims if c.get('company_id') and c['company_id'][0] == 1]
    print(f"  Claims with company_id=1: {len(bad_claims)}")
    for c in bad_claims:
        psql(f"UPDATE clinic_billing_claim SET company_id=3 WHERE id={c['id']};")

    old_claims = [c for c in claims if c.get('date_service') and str(c['date_service'])[:4] == '2024']
    print(f"  Claims with 2024 dates: {len(old_claims)}")

    # Check state distribution
    state_dist = {}
    for c in claims:
        state_dist[c.get('state', 'unknown')] = state_dist.get(c.get('state', 'unknown'), 0) + 1
    print(f"  State distribution: {state_dist}")

    # Get encounters for linking
    all_encounters = search_read('clinic.ehr.encounter',
        fields=['id','patient_id','company_id','encounter_date','physician_id'])
    all_insurers = search_read('clinic.insurer', fields=['id','name','company_id'])

    # Encounters without claims
    enc_with_claims = {c['encounter_id'][0] for c in claims if c.get('encounter_id')}
    enc_without_claims = [e for e in all_encounters if e['id'] not in enc_with_claims]
    print(f"  Encounters without claims: {len(enc_without_claims)}")

    created_claims = 0
    claim_num = 2000
    claim_states_cycle = ['draft', 'submitted', 'paid', 'denied', 'submitted', 'paid', 'paid']

    for idx, enc in enumerate(enc_without_claims[:20]):
        cid = enc.get('company_id', [3])[0] if enc.get('company_id') else 3
        ins_list = [i for i in all_insurers if (i.get('company_id', [cid])[0] if i.get('company_id') else cid) == cid]
        if not ins_list:
            ins_list = all_insurers
        if not ins_list:
            continue

        ins = ins_list[idx % len(ins_list)]
        st = claim_states_cycle[idx % len(claim_states_cycle)]
        claim_num += 1
        amount_billed = round(150 + (idx * 37.5) % 850, 2)
        amount_paid = round(amount_billed * 0.80, 2) if st == 'paid' else 0.0

        claim_data = {
            'patient_id': enc['patient_id'][0],
            'encounter_id': enc['id'],
            'insurer_id': ins['id'],
            'company_id': cid,
            'state': st,
            'date_service': enc.get('encounter_date', '2026-03-10'),
            'amount_billed': amount_billed,
            'amount_paid': amount_paid,
            'claim_number': f"CLM-{claim_num:05d}",
        }
        try:
            new_id = call('clinic.billing.claim', 'create', claim_data)
            created_claims += 1
            if created_claims <= 5:
                print(f"  Created claim id={new_id} state={st} amount={amount_billed}")
        except Exception as e2:
            print(f"  Could not create claim idx={idx}: {e2}")

    # Also create standalone claims for variety
    standalone_claim_templates = [
        (3, 'paid', '2026-03-01', 275.00, 220.00),
        (3, 'denied', '2026-02-20', 500.00, 0.00),
        (4, 'submitted', '2026-03-05', 350.00, 0.00),
        (4, 'paid', '2026-02-28', 180.00, 144.00),
        (5, 'paid', '2026-03-03', 425.00, 340.00),
        (5, 'draft', '2026-03-11', 290.00, 0.00),
        (6, 'submitted', '2026-03-08', 650.00, 0.00),
        (6, 'paid', '2026-02-25', 125.00, 100.00),
    ]
    for idx, (cid, st, dt, billed, paid) in enumerate(standalone_claim_templates):
        claim_num += 1
        pat_list = [p for p in all_patients_enc if (p.get('company_id', [cid])[0] if p.get('company_id') else cid) == cid]
        if not pat_list:
            pat_list = all_patients_enc
        if not pat_list or not all_insurers:
            continue
        ins = all_insurers[idx % len(all_insurers)]
        pat = pat_list[idx % len(pat_list)]
        try:
            new_id = call('clinic.billing.claim', 'create', {
                'patient_id': pat['id'],
                'insurer_id': ins['id'],
                'company_id': cid,
                'state': st,
                'date_service': dt,
                'amount_billed': billed,
                'amount_paid': paid,
                'claim_number': f"CLM-{claim_num:05d}",
            })
            created_claims += 1
        except Exception as e2:
            pass

    print(f"  Created {created_claims} new claims")
    report('clinic.billing.claim', fixed=len(bad_claims), created=created_claims)
except Exception as e:
    print(f"  ERROR: {e}")
    import traceback; traceback.print_exc()

# ─── BILLING CLAIM LINES ──────────────────────────────────────────────────────
print("\n\n### AUDITING: clinic.billing.claim.line ###")
try:
    claim_line_fields = call('clinic.billing.claim.line', 'fields_get', [],
                              {'attributes': ['string', 'type']})
    available_fields = list(claim_line_fields.keys())
    print(f"  Fields: {[f for f in available_fields if not f.startswith('__')]}")

    read_fields = ['id', 'claim_id', 'company_id']
    for f in ['procedure_code', 'diagnosis_code', 'amount_billed', 'amount_paid',
              'quantity', 'unit_price', 'description', 'service_date', 'cpt_code', 'icd10_code']:
        if f in available_fields:
            read_fields.append(f)

    claim_lines = search_read('clinic.billing.claim.line', fields=read_fields)
    print(f"  Total claim lines: {len(claim_lines)}")

    bad_lines = [l for l in claim_lines if l.get('company_id') and l['company_id'][0] == 1]
    for l in bad_lines:
        psql(f"UPDATE clinic_billing_claim_line SET company_id=3 WHERE id={l['id']};")
    print(f"  Fixed {len(bad_lines)} claim lines with company_id=1")

    # Get all claims to add lines
    all_claims = search_read('clinic.billing.claim',
        fields=['id','company_id','amount_billed','state'])
    claims_with_lines = {l['claim_id'][0] for l in claim_lines if l.get('claim_id')}
    claims_without_lines = [c for c in all_claims if c['id'] not in claims_with_lines]
    print(f"  Claims without lines: {len(claims_without_lines)}")

    created_lines = 0
    cpt_codes = [
        ('99213', 'Office visit - established patient, low complexity', 150.00),
        ('99214', 'Office visit - established patient, moderate complexity', 220.00),
        ('99215', 'Office visit - established patient, high complexity', 300.00),
        ('99203', 'Office visit - new patient, low complexity', 175.00),
        ('99204', 'Office visit - new patient, moderate complexity', 250.00),
        ('99205', 'Office visit - new patient, high complexity', 325.00),
        ('93000', 'Electrocardiogram routine ECG with interpretation', 85.00),
        ('85025', 'Complete blood count with differential', 45.00),
        ('80053', 'Comprehensive metabolic panel', 65.00),
        ('71046', 'Chest X-ray 2 views', 120.00),
        ('76700', 'Abdominal ultrasound complete', 280.00),
        ('59400', 'Routine obstetric care including antepartum care', 1800.00),
        ('92002', 'Ophthalmological new patient services', 165.00),
        ('D0150', 'Comprehensive oral evaluation new patient', 95.00),
        ('D0274', 'Bitewing radiographic images 4 images', 75.00),
    ]

    for idx, claim in enumerate(claims_without_lines[:25]):
        cid = claim.get('company_id', [3])[0] if claim.get('company_id') else 3
        # Add 1-3 lines per claim
        n_lines = 1 + (idx % 3)
        for li in range(n_lines):
            cpt = cpt_codes[(idx * 3 + li) % len(cpt_codes)]
            line_data = {
                'claim_id': claim['id'],
                'company_id': cid,
            }
            # Add fields based on what's available
            if 'procedure_code' in available_fields:
                line_data['procedure_code'] = cpt[0]
            if 'cpt_code' in available_fields:
                line_data['cpt_code'] = cpt[0]
            if 'description' in available_fields:
                line_data['description'] = cpt[1]
            if 'amount_billed' in available_fields:
                line_data['amount_billed'] = cpt[2]
            if 'unit_price' in available_fields:
                line_data['unit_price'] = cpt[2]
            if 'quantity' in available_fields:
                line_data['quantity'] = 1
            if 'service_date' in available_fields:
                line_data['service_date'] = '2026-03-11'
            try:
                call('clinic.billing.claim.line', 'create', line_data)
                created_lines += 1
            except Exception as e2:
                if created_lines < 3:
                    print(f"  Could not create claim line: {e2}")
                break  # If first line fails, skip this claim

    print(f"  Created {created_lines} new claim lines")
    report('clinic.billing.claim.line', fixed=len(bad_lines), created=created_lines)
except Exception as e:
    print(f"  ERROR: {e}")
    import traceback; traceback.print_exc()

# ─── EDI TRANSACTIONS ─────────────────────────────────────────────────────────
print("\n\n### AUDITING: clinic.edi.transaction ###")
try:
    edi_fields = call('clinic.edi.transaction', 'fields_get', [],
                      {'attributes': ['string', 'type', 'selection']})
    tx_type_sel = edi_fields.get('transaction_type', {}).get('selection', [])
    state_sel_edi = edi_fields.get('state', {}).get('selection', [])
    print(f"  Transaction types: {tx_type_sel}")
    print(f"  States: {state_sel_edi}")

    avail_fields_edi = list(edi_fields.keys())
    read_fields_edi = ['id', 'name', 'company_id', 'state']
    for f in ['transaction_type', 'claim_id', 'patient_id', 'insurer_id',
              'transaction_date', 'control_number', 'status', 'raw_content']:
        if f in avail_fields_edi:
            read_fields_edi.append(f)

    edi_txns = search_read('clinic.edi.transaction', fields=read_fields_edi)
    print(f"  Total EDI transactions: {len(edi_txns)}")

    bad_edi = [t for t in edi_txns if t.get('company_id') and t['company_id'][0] == 1]
    for t in bad_edi:
        psql(f"UPDATE clinic_edi_transaction SET company_id=3 WHERE id={t['id']};")
    print(f"  Fixed {len(bad_edi)} EDI transactions with company_id=1")

    # State distribution
    state_dist_edi = {}
    for t in edi_txns:
        k = t.get('state', t.get('status', 'unknown'))
        state_dist_edi[k] = state_dist_edi.get(k, 0) + 1
    print(f"  State distribution: {state_dist_edi}")

    # Get all claims for linking
    all_claims_edi = search_read('clinic.billing.claim',
        fields=['id','company_id','insurer_id','patient_id','claim_number','state'])

    tx_types = [s[0] for s in tx_type_sel] if tx_type_sel else ['837p', '835', '270', '271']
    edi_states = [s[0] for s in state_sel_edi] if state_sel_edi else ['pending', 'sent', 'accepted', 'rejected']

    created_edi = 0
    ctrl_num = 10000

    edi_templates = []
    # 837P (claim submissions) for each claim
    for idx, claim in enumerate(all_claims_edi[:10]):
        ctrl_num += 1
        edi_templates.append({
            'company_id': claim.get('company_id', [3])[0] if claim.get('company_id') else 3,
            'transaction_type': '837p' if '837p' in tx_types else tx_types[0],
            'state': edi_states[idx % len(edi_states)],
            'transaction_date': '2026-03-10',
            'control_number': f"{ctrl_num:09d}",
            'claim_id': claim['id'] if 'claim_id' in avail_fields_edi else False,
        })

    # 835 (remittance advice) responses
    for idx in range(5):
        ctrl_num += 1
        cid = [3, 4, 5, 6, 3][idx]
        edi_templates.append({
            'company_id': cid,
            'transaction_type': '835' if '835' in tx_types else (tx_types[1] if len(tx_types) > 1 else tx_types[0]),
            'state': 'accepted' if 'accepted' in edi_states else edi_states[-1],
            'transaction_date': '2026-03-09',
            'control_number': f"{ctrl_num:09d}",
        })

    # 270/271 eligibility
    for idx in range(6):
        ctrl_num += 1
        cid = [3, 4, 5, 6, 3, 4][idx]
        tx_type_270 = '270' if '270' in tx_types else (tx_types[2] if len(tx_types) > 2 else tx_types[0])
        tx_type_271 = '271' if '271' in tx_types else (tx_types[3] if len(tx_types) > 3 else tx_types[0])
        edi_templates.append({
            'company_id': cid,
            'transaction_type': tx_type_270 if idx % 2 == 0 else tx_type_271,
            'state': edi_states[idx % len(edi_states)],
            'transaction_date': '2026-03-11',
            'control_number': f"{ctrl_num:09d}",
        })

    existing_ctrl = {t.get('control_number') for t in edi_txns}

    for tmpl in edi_templates:
        if tmpl.get('control_number') in existing_ctrl:
            continue
        # Remove fields not in model
        data = {k: v for k, v in tmpl.items() if k in avail_fields_edi or k == 'company_id'}
        if 'claim_id' in data and not data['claim_id']:
            del data['claim_id']
        try:
            new_id = call('clinic.edi.transaction', 'create', data)
            created_edi += 1
            existing_ctrl.add(tmpl['control_number'])
        except Exception as e2:
            if created_edi < 3:
                print(f"  Could not create EDI txn: {e2}")

    print(f"  Created {created_edi} new EDI transactions")
    report('clinic.edi.transaction', fixed=len(bad_edi), created=created_edi)
except Exception as e:
    print(f"  ERROR: {e}")
    import traceback; traceback.print_exc()

# ─── REMITTANCE ───────────────────────────────────────────────────────────────
print("\n\n### AUDITING: clinic.remittance ###")
try:
    rem_fields = call('clinic.remittance', 'fields_get', [], {'attributes': ['string', 'type', 'selection']})
    avail_rem = list(rem_fields.keys())
    print(f"  Fields: {[f for f in avail_rem if not f.startswith('__') and not f.startswith('write')]}")

    read_rem = ['id', 'name', 'company_id']
    for f in ['insurer_id', 'state', 'date', 'total_paid', 'era_number', 'check_number',
              'check_date', 'payment_method', 'amount']:
        if f in avail_rem:
            read_rem.append(f)

    remittances = search_read('clinic.remittance', fields=read_rem)
    print(f"  Total remittances: {len(remittances)}")

    bad_rem = [r for r in remittances if r.get('company_id') and r['company_id'][0] == 1]
    for r in bad_rem:
        psql(f"UPDATE clinic_remittance SET company_id=3 WHERE id={r['id']};")
    print(f"  Fixed {len(bad_rem)} remittances with company_id=1")

    all_insurers_rem = search_read('clinic.insurer', fields=['id','name','company_id'])
    state_sel_rem = rem_fields.get('state', {}).get('selection', [])
    rem_states = [s[0] for s in state_sel_rem] if state_sel_rem else ['draft', 'posted', 'reconciled']
    print(f"  Remittance states: {rem_states}")

    created_rem = 0
    era_counter = 5000

    rem_templates = [
        (3, '2026-03-01', 4500.00, 'check', 'CHK-10001'),
        (3, '2026-02-25', 3200.00, 'eft', 'EFT-20001'),
        (3, '2026-02-15', 7800.00, 'eft', 'EFT-20002'),
        (4, '2026-03-05', 2100.00, 'check', 'CHK-10002'),
        (4, '2026-02-20', 1850.00, 'eft', 'EFT-20003'),
        (5, '2026-03-02', 3600.00, 'eft', 'EFT-20004'),
        (5, '2026-02-18', 2900.00, 'check', 'CHK-10003'),
        (6, '2026-03-06', 1200.00, 'eft', 'EFT-20005'),
        (6, '2026-02-22', 980.00, 'check', 'CHK-10004'),
        (3, '2026-01-31', 9200.00, 'eft', 'EFT-20006'),
    ]

    existing_era = {r.get('era_number') for r in remittances if r.get('era_number')}
    existing_check = {r.get('check_number') for r in remittances if r.get('check_number')}

    for idx, (cid, dt, total, method, check_num) in enumerate(rem_templates):
        era_counter += 1
        era_num = f"ERA-{era_counter:05d}"
        if era_num in existing_era or check_num in existing_check:
            continue

        ins_list = [i for i in all_insurers_rem if (i.get('company_id', [cid])[0] if i.get('company_id') else cid) == cid]
        if not ins_list:
            ins_list = all_insurers_rem
        if not ins_list:
            continue

        rem_data = {'company_id': cid}
        if 'insurer_id' in avail_rem:
            rem_data['insurer_id'] = ins_list[idx % len(ins_list)]['id']
        if 'date' in avail_rem:
            rem_data['date'] = dt
        if 'check_date' in avail_rem:
            rem_data['check_date'] = dt
        if 'total_paid' in avail_rem:
            rem_data['total_paid'] = total
        if 'amount' in avail_rem:
            rem_data['amount'] = total
        if 'era_number' in avail_rem:
            rem_data['era_number'] = era_num
        if 'check_number' in avail_rem:
            rem_data['check_number'] = check_num
        if 'payment_method' in avail_rem:
            rem_data['payment_method'] = method
        if 'state' in avail_rem:
            rem_data['state'] = rem_states[idx % len(rem_states)]

        try:
            new_id = call('clinic.remittance', 'create', rem_data)
            created_rem += 1
            existing_era.add(era_num)
        except Exception as e2:
            if created_rem < 3:
                print(f"  Could not create remittance: {e2}")

    print(f"  Created {created_rem} new remittances")
    report('clinic.remittance', fixed=len(bad_rem), created=created_rem)
except Exception as e:
    print(f"  ERROR: {e}")
    import traceback; traceback.print_exc()

# ─── REMITTANCE LINES ─────────────────────────────────────────────────────────
print("\n\n### AUDITING: clinic.remittance.line ###")
try:
    rl_fields = call('clinic.remittance.line', 'fields_get', [], {'attributes': ['string', 'type']})
    avail_rl = list(rl_fields.keys())
    read_rl = ['id', 'company_id', 'remittance_id']
    for f in ['claim_id', 'amount_billed', 'amount_paid', 'adjustment_reason',
              'patient_id', 'claim_number', 'service_date']:
        if f in avail_rl:
            read_rl.append(f)

    rem_lines = search_read('clinic.remittance.line', fields=read_rl)
    print(f"  Total remittance lines: {len(rem_lines)}")

    bad_rl = [l for l in rem_lines if l.get('company_id') and l['company_id'][0] == 1]
    for l in bad_rl:
        psql(f"UPDATE clinic_remittance_line SET company_id=3 WHERE id={l['id']};")
    print(f"  Fixed {len(bad_rl)} remittance lines with company_id=1")

    # Get remittances without lines
    all_rems = search_read('clinic.remittance', fields=['id', 'company_id'])
    rems_with_lines = {l['remittance_id'][0] for l in rem_lines if l.get('remittance_id')}
    rems_without_lines = [r for r in all_rems if r['id'] not in rems_with_lines]
    print(f"  Remittances without lines: {len(rems_without_lines)}")

    all_claims_rl = search_read('clinic.billing.claim',
        fields=['id','patient_id','amount_billed','company_id','claim_number'])
    claims_with_rem_lines = {l['claim_id'][0] for l in rem_lines if l.get('claim_id')}

    created_rl = 0
    adj_reasons = ['CO-45', 'PR-1', 'CO-97', 'PR-2', 'CO-4', 'PI-27']

    for idx, rem in enumerate(rems_without_lines[:15]):
        cid = rem.get('company_id', [3])[0] if rem.get('company_id') else 3
        # Add 2-4 lines per remittance
        n_lines = 2 + (idx % 3)
        available_claims = [c for c in all_claims_rl
                           if (c.get('company_id', [cid])[0] if c.get('company_id') else cid) == cid
                           and c['id'] not in claims_with_rem_lines]

        for li in range(n_lines):
            if li < len(available_claims):
                claim = available_claims[li]
                billed = claim.get('amount_billed', 250.0)
                paid = round(billed * 0.80, 2)
            else:
                billed = round(150 + (idx * li * 37) % 600, 2)
                paid = round(billed * 0.75, 2)
                claim = None

            rl_data = {'remittance_id': rem['id'], 'company_id': cid}
            if claim and 'claim_id' in avail_rl:
                rl_data['claim_id'] = claim['id']
                claims_with_rem_lines.add(claim['id'])
            if 'amount_billed' in avail_rl:
                rl_data['amount_billed'] = billed
            if 'amount_paid' in avail_rl:
                rl_data['amount_paid'] = paid
            if 'adjustment_reason' in avail_rl:
                rl_data['adjustment_reason'] = adj_reasons[(idx + li) % len(adj_reasons)]
            if 'service_date' in avail_rl:
                rl_data['service_date'] = '2026-03-10'

            try:
                call('clinic.remittance.line', 'create', rl_data)
                created_rl += 1
            except Exception as e2:
                if created_rl < 3:
                    print(f"  Could not create remittance line: {e2}")
                break

    print(f"  Created {created_rl} new remittance lines")
    report('clinic.remittance.line', fixed=len(bad_rl), created=created_rl)
except Exception as e:
    print(f"  ERROR: {e}")
    import traceback; traceback.print_exc()

# ─── EHR ENCOUNTER EXTENSIONS ─────────────────────────────────────────────────
for ext_model, spec_name in [
    ('clinic.ehr.encounter.gynecology', 'Gynecology'),
    ('clinic.ehr.encounter.ophthalmology', 'Ophthalmology'),
    ('clinic.ehr.encounter.stomatology', 'Stomatology'),
]:
    print(f"\n\n### AUDITING: {ext_model} ###")
    try:
        ext_fields = call(ext_model, 'fields_get', [], {'attributes': ['string', 'type']})
        avail_ext = list(ext_fields.keys())
        print(f"  Available fields: {[f for f in avail_ext if not f.startswith('__') and not f.startswith('write') and not f.startswith('create') and not f.startswith('display')]}")

        read_ext = ['id', 'company_id', 'encounter_id'] + [f for f in avail_ext
                   if f not in ('id', 'company_id', 'encounter_id') and not f.startswith('__')
                   and not f.startswith('write') and not f.startswith('create')
                   and not f.startswith('display') and not f.startswith('message')
                   and not f.startswith('activity') and f != 'name'][:15]

        ext_records = search_read(ext_model, fields=read_ext)
        print(f"  Total records: {len(ext_records)}")

        bad_ext = [r for r in ext_records if r.get('company_id') and r['company_id'][0] == 1]
        table_name = ext_model.replace('.', '_')
        for r in bad_ext:
            psql(f"UPDATE {table_name} SET company_id=3 WHERE id={r['id']};")
        print(f"  Fixed {len(bad_ext)} records with company_id=1")

        # Get specialty encounters
        spec_id_map = {'Gynecology': 2, 'Ophthalmology': 3, 'Stomatology': 4}
        spec_id = spec_id_map.get(spec_name, 1)
        spec_encounters = search_read('clinic.ehr.encounter',
            fields=['id', 'company_id', 'patient_id'],
            domain=[('specialty_id', '=', spec_id)])
        print(f"  Encounters for {spec_name} (spec_id={spec_id}): {len(spec_encounters)}")

        enc_with_ext = {r['encounter_id'][0] for r in ext_records if r.get('encounter_id')}
        enc_without_ext = [e for e in spec_encounters if e['id'] not in enc_with_ext]
        print(f"  Encounters without extension record: {len(enc_without_ext)}")

        created_ext = 0
        for enc in enc_without_ext[:10]:
            cid = enc.get('company_id', [3])[0] if enc.get('company_id') else 3
            ext_data = {'encounter_id': enc['id'], 'company_id': cid}

            # Add specialty-specific fields
            if spec_name == 'Gynecology':
                if 'gravida' in avail_ext: ext_data['gravida'] = 2
                if 'para' in avail_ext: ext_data['para'] = 1
                if 'lmp' in avail_ext: ext_data['lmp'] = '2026-02-01'
                if 'gest_weeks' in avail_ext: ext_data['gest_weeks'] = 28
                if 'contraception' in avail_ext: ext_data['contraception'] = 'oral_pills'
            elif spec_name == 'Ophthalmology':
                if 'od_sphere' in avail_ext: ext_data['od_sphere'] = -2.25
                if 'os_sphere' in avail_ext: ext_data['os_sphere'] = -2.50
                if 'od_cylinder' in avail_ext: ext_data['od_cylinder'] = -0.75
                if 'os_cylinder' in avail_ext: ext_data['os_cylinder'] = -0.50
                if 'od_axis' in avail_ext: ext_data['od_axis'] = 90
                if 'os_axis' in avail_ext: ext_data['os_axis'] = 85
                if 'iop_od' in avail_ext: ext_data['iop_od'] = 15.0
                if 'iop_os' in avail_ext: ext_data['iop_os'] = 16.0
                if 'va_od' in avail_ext: ext_data['va_od'] = '20/40'
                if 'va_os' in avail_ext: ext_data['va_os'] = '20/30'
            elif spec_name == 'Stomatology':
                if 'teeth_examined' in avail_ext: ext_data['teeth_examined'] = 32
                if 'caries_count' in avail_ext: ext_data['caries_count'] = 2
                if 'plaque_index' in avail_ext: ext_data['plaque_index'] = 1.5
                if 'gingival_index' in avail_ext: ext_data['gingival_index'] = 0.8
                if 'treatment_plan' in avail_ext: ext_data['treatment_plan'] = 'Filling for teeth #14 and #18. Cleaning scheduled.'

            try:
                new_id = call(ext_model, 'create', ext_data)
                created_ext += 1
            except Exception as e2:
                if created_ext < 3:
                    print(f"  Could not create {spec_name} ext record: {e2}")

        print(f"  Created {created_ext} new {spec_name} extension records")
        report(ext_model, fixed=len(bad_ext), created=created_ext)
    except Exception as e:
        print(f"  Model not found or error: {e}")

# ─── COVERAGE RULES ────────────────────────────────────────────────────────────
print("\n\n### AUDITING: clinic.coverage.rule ###")
try:
    cov_fields = call('clinic.coverage.rule', 'fields_get', [], {'attributes': ['string', 'type', 'selection']})
    avail_cov = list(cov_fields.keys())

    cov_records = search_read('clinic.coverage.rule',
        fields=['id', 'name', 'company_id'] +
                [f for f in ['insurer_id', 'plan_id', 'procedure_code', 'covered',
                             'requires_auth', 'copay', 'coinsurance', 'max_visits'] if f in avail_cov])
    print(f"  Total coverage rules: {len(cov_records)}")

    bad_cov = [r for r in cov_records if r.get('company_id') and r['company_id'][0] == 1]
    for r in bad_cov:
        psql(f"UPDATE clinic_coverage_rule SET company_id=3 WHERE id={r['id']};")
    print(f"  Fixed {len(bad_cov)} coverage rules with company_id=1")

    # Create additional coverage rules for better variety
    all_plans_cov = search_read('clinic.insurer.plan', fields=['id','insurer_id','company_id'])

    new_rules = [
        ('Annual Wellness Visit', '99387', True, False, 0.0, 0.0, 1),
        ('Emergency Room Visit', '99285', True, False, 150.0, 0.20, None),
        ('MRI Brain w/ contrast', '70553', True, True, 50.0, 0.20, 1),
        ('CT Scan Abdomen/Pelvis', '74177', True, True, 50.0, 0.20, None),
        ('Physical Therapy', '97010', True, True, 30.0, 0.20, 30),
        ('Colonoscopy screening', '45378', True, False, 0.0, 0.0, 1),
        ('Mammogram screening', '77067', True, False, 0.0, 0.0, 1),
        ('Mental Health - Individual', '90834', True, False, 30.0, 0.20, 52),
        ('Chiropractic - Spinal manip', '98940', True, False, 25.0, 0.20, 20),
        ('Prescription Drugs Tier 1', None, True, False, 10.0, 0.0, None),
    ]

    existing_rule_names = {r['name'] for r in cov_records}
    created_cov = 0

    for idx, (name, code, covered, req_auth, copay, coins, max_v) in enumerate(new_rules):
        if name in existing_rule_names:
            continue
        if not all_plans_cov:
            break
        plan = all_plans_cov[idx % len(all_plans_cov)]
        cid = plan.get('company_id', [3])[0] if plan.get('company_id') else 3

        rule_data = {'name': name, 'company_id': cid}
        if 'plan_id' in avail_cov:
            rule_data['plan_id'] = plan['id']
        if 'insurer_id' in avail_cov:
            rule_data['insurer_id'] = plan['insurer_id'][0] if plan.get('insurer_id') else False
        if 'procedure_code' in avail_cov and code:
            rule_data['procedure_code'] = code
        if 'covered' in avail_cov:
            rule_data['covered'] = covered
        if 'requires_auth' in avail_cov:
            rule_data['requires_auth'] = req_auth
        if 'copay' in avail_cov:
            rule_data['copay'] = copay
        if 'coinsurance' in avail_cov:
            rule_data['coinsurance'] = coins
        if 'max_visits' in avail_cov and max_v:
            rule_data['max_visits'] = max_v

        try:
            call('clinic.coverage.rule', 'create', rule_data)
            created_cov += 1
        except Exception as e2:
            if created_cov < 3:
                print(f"  Could not create coverage rule '{name}': {e2}")

    print(f"  Created {created_cov} new coverage rules")
    report('clinic.coverage.rule', fixed=len(bad_cov), created=created_cov)
except Exception as e:
    print(f"  ERROR: {e}")
    import traceback; traceback.print_exc()

# ─── EDI ELIGIBILITY REQUESTS ─────────────────────────────────────────────────
print("\n\n### AUDITING: clinic.edi.eligibility.request ###")
try:
    elig_fields = call('clinic.edi.eligibility.request', 'fields_get', [],
                       {'attributes': ['string', 'type', 'selection']})
    avail_elig = list(elig_fields.keys())
    state_sel_elig = elig_fields.get('state', {}).get('selection', [])
    elig_states = [s[0] for s in state_sel_elig] if state_sel_elig else ['draft', 'sent', 'received']

    read_elig = ['id', 'name', 'company_id', 'state'] + \
                [f for f in ['patient_id', 'insurer_id', 'policy_id', 'request_date',
                             'response_date', 'eligible', 'control_number'] if f in avail_elig]

    elig_recs = search_read('clinic.edi.eligibility.request', fields=read_elig)
    print(f"  Total eligibility requests: {len(elig_recs)}")

    bad_elig = [r for r in elig_recs if r.get('company_id') and r['company_id'][0] == 1]
    for r in bad_elig:
        psql(f"UPDATE clinic_edi_eligibility_request SET company_id=3 WHERE id={r['id']};")

    state_dist_elig = {}
    for r in elig_recs:
        k = r.get('state', 'unknown')
        state_dist_elig[k] = state_dist_elig.get(k, 0) + 1
    print(f"  State distribution: {state_dist_elig}")

    all_pols = search_read('clinic.patient.policy', fields=['id','patient_id','insurer_id','company_id'])
    existing_pat_elig = {r['patient_id'][0] for r in elig_recs if r.get('patient_id')}

    created_elig = 0
    ctrl_elig = 50000

    for idx, pol in enumerate(all_pols[:15]):
        if pol.get('patient_id') and pol['patient_id'][0] in existing_pat_elig:
            continue
        cid = pol.get('company_id', [3])[0] if pol.get('company_id') else 3
        ctrl_elig += 1
        state = elig_states[idx % len(elig_states)]

        elig_data = {'company_id': cid, 'state': state}
        if 'patient_id' in avail_elig and pol.get('patient_id'):
            elig_data['patient_id'] = pol['patient_id'][0]
        if 'insurer_id' in avail_elig and pol.get('insurer_id'):
            elig_data['insurer_id'] = pol['insurer_id'][0]
        if 'policy_id' in avail_elig:
            elig_data['policy_id'] = pol['id']
        if 'request_date' in avail_elig:
            elig_data['request_date'] = '2026-03-11'
        if 'control_number' in avail_elig:
            elig_data['control_number'] = f"{ctrl_elig:09d}"
        if 'eligible' in avail_elig:
            elig_data['eligible'] = state in ('received', 'accepted')
        if 'response_date' in avail_elig and state in ('received', 'accepted'):
            elig_data['response_date'] = '2026-03-11'

        try:
            call('clinic.edi.eligibility.request', 'create', elig_data)
            created_elig += 1
            if pol.get('patient_id'):
                existing_pat_elig.add(pol['patient_id'][0])
        except Exception as e2:
            if created_elig < 3:
                print(f"  Could not create eligibility request: {e2}")

    print(f"  Created {created_elig} new eligibility requests")
    report('clinic.edi.eligibility.request', fixed=len(bad_elig), created=created_elig)
except Exception as e:
    print(f"  ERROR: {e}")
    import traceback; traceback.print_exc()

# ─── PHYSICIAN SCHEDULES ──────────────────────────────────────────────────────
print("\n\n### AUDITING: clinic.physician.schedule ###")
try:
    sched_fields = call('clinic.physician.schedule', 'fields_get', [],
                        {'attributes': ['string', 'type', 'selection']})
    avail_sched = list(sched_fields.keys())
    day_sel = sched_fields.get('day_of_week', {}).get('selection', [])
    print(f"  Day of week options: {day_sel}")

    read_sched = ['id', 'name', 'company_id', 'physician_id'] + \
                 [f for f in ['day_of_week', 'start_time', 'end_time', 'active',
                              'slot_duration', 'max_patients'] if f in avail_sched]
    schedules = search_read('clinic.physician.schedule', fields=read_sched)
    print(f"  Total schedules: {len(schedules)}")

    bad_sched = [s for s in schedules if s.get('company_id') and s['company_id'][0] == 1]
    for s in bad_sched:
        psql(f"UPDATE clinic_physician_schedule SET company_id=3 WHERE id={s['id']};")
    print(f"  Fixed {len(bad_sched)} schedules with company_id=1")

    # Check which physicians have no schedule
    all_phys_sched = search_read('clinic.physician', fields=['id','name','company_id'])
    phys_with_sched = {s['physician_id'][0] for s in schedules if s.get('physician_id')}
    phys_without_sched = [p for p in all_phys_sched if p['id'] not in phys_with_sched]
    print(f"  Physicians without schedule: {len(phys_without_sched)}")

    days = [s[0] for s in day_sel] if day_sel else ['0','1','2','3','4']
    created_sched = 0

    for idx, ph in enumerate(phys_without_sched):
        cid = ph.get('company_id', [3])[0] if ph.get('company_id') else 3
        # Create Mon-Fri schedule
        for day_idx, day in enumerate(days[:5]):
            sched_data = {
                'physician_id': ph['id'],
                'company_id': cid,
            }
            if 'day_of_week' in avail_sched:
                sched_data['day_of_week'] = day
            if 'start_time' in avail_sched:
                sched_data['start_time'] = 8.0
            if 'end_time' in avail_sched:
                sched_data['end_time'] = 17.0
            if 'active' in avail_sched:
                sched_data['active'] = True
            if 'slot_duration' in avail_sched:
                sched_data['slot_duration'] = 30
            if 'max_patients' in avail_sched:
                sched_data['max_patients'] = 16

            try:
                call('clinic.physician.schedule', 'create', sched_data)
                created_sched += 1
            except Exception as e2:
                if created_sched < 3:
                    print(f"  Could not create schedule: {e2}")
                break

    print(f"  Created {created_sched} new schedules")
    report('clinic.physician.schedule', fixed=len(bad_sched), created=created_sched)
except Exception as e:
    print(f"  ERROR: {e}")
    import traceback; traceback.print_exc()

# ─── EHR DIAGNOSES ────────────────────────────────────────────────────────────
print("\n\n### AUDITING: clinic.ehr.diagnosis ###")
try:
    diag_fields = call('clinic.ehr.diagnosis', 'fields_get', [],
                       {'attributes': ['string', 'type', 'selection']})
    avail_diag = list(diag_fields.keys())

    read_diag = ['id', 'name', 'company_id'] + \
                [f for f in ['encounter_id', 'icd10_code', 'icd10_description',
                             'diagnosis_type', 'is_primary', 'notes'] if f in avail_diag]
    diagnoses = search_read('clinic.ehr.diagnosis', fields=read_diag)
    print(f"  Total diagnoses: {len(diagnoses)}")

    bad_diag = [d for d in diagnoses if d.get('company_id') and d['company_id'][0] == 1]
    for d in bad_diag:
        psql(f"UPDATE clinic_ehr_diagnosis SET company_id=3 WHERE id={d['id']};")
    print(f"  Fixed {len(bad_diag)} diagnoses with company_id=1")

    # Get encounters without diagnoses
    all_encs_diag = search_read('clinic.ehr.encounter', fields=['id','company_id','specialty_id'])
    encs_with_diag = {d['encounter_id'][0] for d in diagnoses if d.get('encounter_id')}
    encs_without_diag = [e for e in all_encs_diag if e['id'] not in encs_with_diag]
    print(f"  Encounters without diagnoses: {len(encs_without_diag)}")

    icd10_by_specialty = {
        1: [  # General Medicine
            ('Z00.00', 'Encounter for general adult medical examination without abnormal findings'),
            ('I10', 'Essential (primary) hypertension'),
            ('E11.9', 'Type 2 diabetes mellitus without complications'),
            ('J06.9', 'Acute upper respiratory infection, unspecified'),
            ('M54.5', 'Low back pain'),
            ('R51', 'Headache'),
            ('J18.9', 'Pneumonia, unspecified organism'),
            ('K21.0', 'Gastro-esophageal reflux disease with esophagitis'),
        ],
        2: [  # Gynecology
            ('Z34.20', 'Encounter for supervision of normal pregnancy, second trimester, unspecified'),
            ('Z01.411', 'Encounter for gynecological examination with abnormal findings'),
            ('N94.6', 'Dysmenorrhoea, unspecified'),
            ('O80', 'Encounter for full-term uncomplicated delivery'),
        ],
        3: [  # Ophthalmology
            ('H52.13', 'Myopia, bilateral'),
            ('H40.9', 'Unspecified glaucoma'),
            ('H26.9', 'Unspecified cataract'),
            ('H04.123', 'Dry eye syndrome of bilateral lacrimal glands'),
        ],
        4: [  # Stomatology
            ('K02.9', 'Dental caries, unspecified'),
            ('K05.10', 'Chronic gingivitis, plaque induced'),
            ('K08.409', 'Complete loss of teeth, unspecified cause, unspecified class'),
            ('K04.0', 'Pulpitis'),
        ],
    }

    diag_types = diag_fields.get('diagnosis_type', {}).get('selection', [])
    dtype_vals = [s[0] for s in diag_types] if diag_types else ['primary', 'secondary']

    created_diag = 0
    for idx, enc in enumerate(encs_without_diag[:30]):
        cid = enc.get('company_id', [3])[0] if enc.get('company_id') else 3
        spec_id = enc.get('specialty_id', [1])[0] if enc.get('specialty_id') else 1
        icd_list = icd10_by_specialty.get(spec_id, icd10_by_specialty[1])
        icd = icd_list[idx % len(icd_list)]

        diag_data = {
            'encounter_id': enc['id'],
            'company_id': cid,
        }
        if 'icd10_code' in avail_diag:
            diag_data['icd10_code'] = icd[0]
        if 'icd10_description' in avail_diag:
            diag_data['icd10_description'] = icd[1]
        if 'name' in avail_diag:
            diag_data['name'] = icd[1][:80]
        if 'is_primary' in avail_diag:
            diag_data['is_primary'] = True
        if 'diagnosis_type' in avail_diag:
            diag_data['diagnosis_type'] = dtype_vals[0]

        try:
            call('clinic.ehr.diagnosis', 'create', diag_data)
            created_diag += 1
        except Exception as e2:
            if created_diag < 3:
                print(f"  Could not create diagnosis: {e2}")

    print(f"  Created {created_diag} new diagnoses")
    report('clinic.ehr.diagnosis', fixed=len(bad_diag), created=created_diag)
except Exception as e:
    print(f"  ERROR: {e}")
    import traceback; traceback.print_exc()

# ─── PATIENT CONSENTS ─────────────────────────────────────────────────────────
print("\n\n### AUDITING: clinic.patient.consent ###")
try:
    consent_fields = call('clinic.patient.consent', 'fields_get', [],
                          {'attributes': ['string', 'type', 'selection']})
    avail_consent = list(consent_fields.keys())
    state_sel_con = consent_fields.get('state', {}).get('selection', [])
    con_states = [s[0] for s in state_sel_con] if state_sel_con else ['draft','signed','revoked']

    read_consent = ['id', 'name', 'company_id', 'patient_id', 'state'] + \
                   [f for f in ['consent_type', 'signed_date', 'expiry_date',
                                'physician_id', 'encounter_id'] if f in avail_consent]
    consents = search_read('clinic.patient.consent', fields=read_consent)
    print(f"  Total consents: {len(consents)}")

    bad_con = [c for c in consents if c.get('company_id') and c['company_id'][0] == 1]
    for c in bad_con:
        psql(f"UPDATE clinic_patient_consent SET company_id=3 WHERE id={c['id']};")
    print(f"  Fixed {len(bad_con)} consents with company_id=1")

    # Check for expired or 2024 dates
    old_con = [c for c in consents if c.get('expiry_date') and c['expiry_date'] < '2025-01-01']
    print(f"  Consents with expired dates (<2025): {len(old_con)}")
    for c in old_con:
        try:
            call('clinic.patient.consent', 'write', [c['id']], {'expiry_date': '2027-12-31'})
        except:
            pass

    # Get patients without consents
    all_pats_con = search_read('clinic.patient', fields=['id','name','company_id'])
    pats_with_consent = {c['patient_id'][0] for c in consents if c.get('patient_id')}
    pats_without_consent = [p for p in all_pats_con if p['id'] not in pats_with_consent]
    print(f"  Patients without consent: {len(pats_without_consent)}")

    consent_types_sel = consent_fields.get('consent_type', {}).get('selection', [])
    consent_types = [s[0] for s in consent_types_sel] if consent_types_sel else \
                    ['general', 'surgery', 'anesthesia', 'photography', 'research']

    created_con = 0
    all_phys_con = search_read('clinic.physician', fields=['id','company_id'])

    for idx, pat in enumerate(pats_without_consent[:20]):
        cid = pat.get('company_id', [3])[0] if pat.get('company_id') else 3
        phys_list = [p for p in all_phys_con if (p.get('company_id', [cid])[0] if p.get('company_id') else cid) == cid]
        if not phys_list:
            phys_list = all_phys_con

        con_data = {
            'patient_id': pat['id'],
            'company_id': cid,
            'state': con_states[idx % len(con_states)],
        }
        if 'consent_type' in avail_consent:
            con_data['consent_type'] = consent_types[idx % len(consent_types)]
        if 'signed_date' in avail_consent:
            con_data['signed_date'] = '2026-01-15'
        if 'expiry_date' in avail_consent:
            con_data['expiry_date'] = '2027-12-31'
        if 'physician_id' in avail_consent and phys_list:
            con_data['physician_id'] = phys_list[idx % len(phys_list)]['id']

        try:
            call('clinic.patient.consent', 'create', con_data)
            created_con += 1
        except Exception as e2:
            if created_con < 3:
                print(f"  Could not create consent: {e2}")

    print(f"  Created {created_con} new consents")
    report('clinic.patient.consent', fixed=len(bad_con)+len(old_con), created=created_con)
except Exception as e:
    print(f"  ERROR: {e}")
    import traceback; traceback.print_exc()

# ─── APPOINTMENTS ─────────────────────────────────────────────────────────────
print("\n\n### AUDITING: clinic.appointment ###")
try:
    appt_fields = call('clinic.appointment', 'fields_get', [],
                       {'attributes': ['string', 'type', 'selection']})
    avail_appt = list(appt_fields.keys())
    state_sel_appt = appt_fields.get('state', {}).get('selection', [])
    appt_states = [s[0] for s in state_sel_appt] if state_sel_appt else \
                  ['draft', 'confirmed', 'done', 'cancelled', 'no_show']

    read_appt = ['id', 'name', 'company_id', 'patient_id', 'state'] + \
                [f for f in ['physician_id', 'appointment_date', 'appointment_datetime',
                             'duration', 'specialty_id', 'reason'] if f in avail_appt]
    appointments = search_read('clinic.appointment', fields=read_appt)
    print(f"  Total appointments: {len(appointments)}")

    bad_appt = [a for a in appointments if a.get('company_id') and a['company_id'][0] == 1]
    for a in bad_appt:
        psql(f"UPDATE clinic_appointment SET company_id=3 WHERE id={a['id']};")

    old_appt = [a for a in appointments
                if (a.get('appointment_date') and str(a['appointment_date'])[:4] == '2024') or
                   (a.get('appointment_datetime') and str(a['appointment_datetime'])[:4] == '2024')]
    print(f"  Appointments with 2024 dates: {len(old_appt)}")

    state_dist_appt = {}
    for a in appointments:
        k = a.get('state', 'unknown')
        state_dist_appt[k] = state_dist_appt.get(k, 0) + 1
    print(f"  State distribution: {state_dist_appt}")
    print(f"  Fixed {len(bad_appt)} appointments with company_id=1")

    # Create future appointments for variety
    all_phys_appt = search_read('clinic.physician', fields=['id','company_id','specialty_id'])
    all_pats_appt = search_read('clinic.patient', fields=['id','company_id'])

    company_phys_appt = {}
    for ph in all_phys_appt:
        cid = ph.get('company_id', [3])[0] if ph.get('company_id') else 3
        company_phys_appt.setdefault(cid, []).append(ph)

    future_dates = [
        '2026-03-12 09:00:00', '2026-03-12 10:00:00', '2026-03-12 11:00:00',
        '2026-03-13 09:30:00', '2026-03-13 14:00:00', '2026-03-16 09:00:00',
        '2026-03-16 10:30:00', '2026-03-17 08:00:00', '2026-03-17 15:00:00',
        '2026-03-18 09:00:00', '2026-03-19 10:00:00', '2026-03-20 09:30:00',
        '2026-03-23 09:00:00', '2026-03-24 14:00:00', '2026-03-25 10:00:00',
    ]

    created_appt = 0
    appt_companies = [3, 3, 4, 4, 5, 5, 6, 3, 4, 5, 6, 3, 4, 5, 6]

    for idx, (dt, cid) in enumerate(zip(future_dates, appt_companies)):
        phys_list = company_phys_appt.get(cid, company_phys_appt.get(3, []))
        pat_list = [p for p in all_pats_appt if (p.get('company_id', [cid])[0] if p.get('company_id') else cid) == cid]
        if not phys_list or not pat_list:
            continue

        ph = phys_list[idx % len(phys_list)]
        pat = pat_list[idx % len(pat_list)]
        spec_id = ph.get('specialty_id', [1])[0] if ph.get('specialty_id') else 1

        appt_data = {
            'patient_id': pat['id'],
            'physician_id': ph['id'],
            'company_id': cid,
            'state': 'confirmed',
        }
        if 'appointment_datetime' in avail_appt:
            appt_data['appointment_datetime'] = dt
        if 'appointment_date' in avail_appt:
            appt_data['appointment_date'] = dt[:10]
        if 'duration' in avail_appt:
            appt_data['duration'] = 30
        if 'specialty_id' in avail_appt:
            appt_data['specialty_id'] = spec_id
        reasons = ['Follow-up visit', 'Annual checkup', 'New patient consultation',
                   'Lab results review', 'Medication management', 'Referral evaluation']
        if 'reason' in avail_appt:
            appt_data['reason'] = reasons[idx % len(reasons)]

        try:
            call('clinic.appointment', 'create', appt_data)
            created_appt += 1
        except Exception as e2:
            if created_appt < 3:
                print(f"  Could not create appointment: {e2}")

    print(f"  Created {created_appt} new appointments")
    report('clinic.appointment', fixed=len(bad_appt), created=created_appt)
except Exception as e:
    print(f"  ERROR: {e}")
    import traceback; traceback.print_exc()

# ─── FINAL SUMMARY ────────────────────────────────────────────────────────────
print("\n\n" + "=" * 70)
print("FINAL SUMMARY")
print("=" * 70)
print(f"{'Model':<45} {'Fixed':>6} {'Created':>8} {'Final Count':>12}")
print("-" * 75)

total_fixed = 0
total_created = 0

for model, data in sorted(summary.items()):
    final = data.get('final', '?')
    fixed = data.get('fixed', 0)
    created = data.get('created', 0)
    total_fixed += fixed
    total_created += created
    print(f"{model:<45} {fixed:>6} {created:>8} {final:>12}")

print("-" * 75)
print(f"{'TOTAL':<45} {total_fixed:>6} {total_created:>8}")
print("=" * 70)
print("\nAudit complete. All models reviewed and improved.")
