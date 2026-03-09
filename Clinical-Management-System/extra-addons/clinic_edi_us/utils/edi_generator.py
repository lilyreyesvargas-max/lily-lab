"""
X12 EDI Generator/Parser utilities for US Healthcare EDI.
Supports: 837P (Professional Claim), 270 (Eligibility Request),
          835 (Remittance Advice), 271 (Eligibility Response).
"""
from datetime import datetime


def _ts():
    return datetime.utcnow().strftime("%Y%m%d%H%M%S")


def _date():
    return datetime.utcnow().strftime("%y%m%d")


def _time():
    return datetime.utcnow().strftime("%H%M")


def _ctrl():
    return f"{int(datetime.utcnow().timestamp()) % 999999999:09d}"


def generate_837p(claim, config):
    """
    Generate X12 837P Professional Claim from a clinic.billing.claim record.
    Returns X12 string with ~ segment terminators.
    """
    ctrl = _ctrl()
    sender = config.sender_id if config else 'SENDCLINIC'
    receiver = config.receiver_id if config else 'CLRHOUSE'
    date_str = _date()
    time_str = _time()
    svc_date = (claim.service_date.strftime('%Y%m%d') if claim.service_date
                else datetime.utcnow().strftime('%Y%m%d'))

    patient = claim.patient_id
    policy = claim.policy_id
    insurer = policy.insurer_id if policy else None

    patient_last = ''
    patient_first = ''
    if patient and patient.name:
        parts = patient.name.split(' ', 1)
        patient_last = parts[0]
        patient_first = parts[1] if len(parts) > 1 else ''

    payer_id = insurer.payer_id if insurer else 'PAYER001'
    member_id = policy.member_id if policy else 'MEMBER001'

    total = sum(l.subtotal for l in claim.line_ids) if claim.line_ids else claim.total_amount

    segs = [
        f"ISA*00*          *00*          *ZZ*{sender:<15}*ZZ*{receiver:<15}*{date_str}*{time_str}*^*00501*{ctrl}*0*T*:~",
        f"GS*HC*{sender}*{receiver}*{datetime.utcnow().strftime('%Y%m%d')}*{time_str}*1*X*005010X222A1~",
        "ST*837*0001*005010X222A1~",
        f"NM1*41*2*{claim.company_id.name if claim.company_id else 'CLINIC'}*****46*TIN000000000~",
        "PER*IC*BILLING*TE*5550000000~",
        f"NM1*40*2*{receiver}*****46*987654321~",
        "HL*1**20*1~",
        f"NM1*85*2*{claim.company_id.name if claim.company_id else 'CLINIC'}*****XX*1234567890~",
        "N3*123 MEDICAL DR~",
        "N4*NEW YORK*NY*10001~",
        "REF*EI*TIN000000000~",
        "HL*2*1*22*0~",
        "SBR*P*18*GROUP001**PPO***CI~",
        f"NM1*IL*1*{patient_last}*{patient_first}****MI*{member_id}~",
        f"NM1*PR*2*INSURER*****PI*{payer_id}~",
        f"CLM*{claim.name}*{total:.2f}***11:B:1*Y*A*Y*I~",
        f"DTP*434*D8*{svc_date}~",
        "HI*ABK:Z00.00~",
        "NM1*82*1*PHYSICIAN*DOC***MD*XX*1234567890~",
    ]

    for i, line in enumerate(claim.line_ids or [], 1):
        segs += [
            f"LX*{i}~",
            f"SV1*HC:{line.service_code}**{line.subtotal:.2f}*UN*{line.quantity:.0f}***1~",
            f"DTP*472*D8*{svc_date}~",
        ]

    if not claim.line_ids:
        segs += [
            "LX*1~",
            f"SV1*HC:99213**{total:.2f}*UN*1***1~",
            f"DTP*472*D8*{svc_date}~",
        ]

    body_count = len(segs) - 2  # exclude ISA, IEA
    segs += [
        f"SE*{body_count - 2}*0001~",
        "GE*1*1~",
        f"IEA*1*{ctrl}~",
    ]
    return "\n".join(segs)


def generate_270(eligibility_req, config):
    """Generate X12 270 Eligibility Request."""
    ctrl = _ctrl()
    sender = config.sender_id if config else 'SENDCLINIC'
    receiver = config.receiver_id if config else 'CLRHOUSE'
    date_str = _date()
    time_str = _time()
    today = datetime.utcnow().strftime('%Y%m%d')

    patient = eligibility_req.patient_id
    insurer = eligibility_req.insurer_id
    payer_id = insurer.payer_id if insurer else 'PAYER001'

    patient_last = ''
    patient_first = ''
    dob = ''
    gender = ''
    if patient:
        parts = (patient.name or '').split(' ', 1)
        patient_last = parts[0]
        patient_first = parts[1] if len(parts) > 1 else ''
        if patient.date_of_birth:
            dob = patient.date_of_birth.strftime('%Y%m%d')
        gender = 'M' if patient.gender == 'male' else 'F' if patient.gender == 'female' else 'U'

    segs = [
        f"ISA*00*          *00*          *ZZ*{sender:<15}*ZZ*{receiver:<15}*{date_str}*{time_str}*^*00501*{ctrl}*0*T*:~",
        f"GS*HS*{sender}*{receiver}*{today}*{time_str}*1*X*005010X279A1~",
        "ST*270*0001*005010X279A1~",
        f"BHT*0022*13*{ctrl}*{today}*{time_str}~",
        "HL*1**20*1~",
        f"NM1*PR*2*{insurer.name if insurer else 'INSURER'}*****PI*{payer_id}~",
        "HL*2*1*21*1~",
        "NM1*1P*2*MAIN CLINIC*****XX*1234567890~",
        "HL*3*2*22*0~",
        f"TRN*1*{ctrl}*9877281234~",
        f"NM1*IL*1*{patient_last}*{patient_first}***~",
    ]
    if dob and gender:
        segs.append(f"DMG*D8*{dob}*{gender}~")
    segs += [
        f"DTP*291*D8*{today}~",
        "EQ*30~",
        f"SE*{len(segs) - 2}*0001~",
        "GE*1*1~",
        f"IEA*1*{ctrl}~",
    ]
    return "\n".join(segs)


def parse_835(content: str) -> dict:
    """Parse X12 835 ERA. Returns structured dict."""
    segments = [s.strip() for s in content.replace('\n', '').split('~') if s.strip()]
    result = {
        'control_number': '', 'check_number': '', 'payment_date': '',
        'total_paid': 0.0, 'payer_name': '', 'payee_name': '', 'claims': []
    }
    current_claim = None

    for seg in segments:
        parts = seg.split('*')
        tag = parts[0]

        if tag == 'ISA' and len(parts) > 13:
            result['control_number'] = parts[13]
        elif tag == 'TRN' and len(parts) > 2:
            result['check_number'] = parts[2]
        elif tag == 'BPR' and len(parts) > 2:
            try:
                result['total_paid'] = float(parts[2])
            except (ValueError, IndexError):
                pass
        elif tag == 'DTM' and len(parts) > 2 and parts[1] == '405':
            result['payment_date'] = parts[2]
        elif tag == 'N1' and len(parts) > 2:
            if parts[1] == 'PR':
                result['payer_name'] = parts[2]
            elif parts[1] == 'PE':
                result['payee_name'] = parts[2]
        elif tag == 'CLP' and len(parts) > 4:
            current_claim = {
                'claim_ref': parts[1],
                'status_code': parts[2],
                'billed': _safe_float(parts[3]),
                'paid': _safe_float(parts[4]),
                'services': []
            }
            result['claims'].append(current_claim)
        elif tag == 'SVC' and current_claim and len(parts) > 3:
            current_claim['services'].append({
                'code': parts[1],
                'billed': _safe_float(parts[2]),
                'paid': _safe_float(parts[3]),
            })

    return result


def parse_271(content: str) -> dict:
    """Parse X12 271 Eligibility Response. Returns eligibility dict."""
    segments = [s.strip() for s in content.replace('\n', '').split('~') if s.strip()]
    result = {
        'control_number': '', 'payer_name': '', 'member': {},
        'eligible': False, 'plan_name': '', 'deductible': None,
        'deductible_met': None, 'copay': None, 'eligibility_items': []
    }

    for seg in segments:
        parts = seg.split('*')
        tag = parts[0]

        if tag == 'ISA' and len(parts) > 13:
            result['control_number'] = parts[13]
        elif tag == 'N1' and len(parts) > 2 and parts[1] == 'PR':
            result['payer_name'] = parts[2]
        elif tag == 'NM1' and len(parts) > 3 and parts[1] == 'IL':
            result['member'] = {
                'last_name': parts[3] if len(parts) > 3 else '',
                'first_name': parts[4] if len(parts) > 4 else '',
                'member_id': parts[9] if len(parts) > 9 else '',
            }
        elif tag == 'EB' and len(parts) > 1:
            eb = {
                'code': parts[1],
                'coverage_level': parts[2] if len(parts) > 2 else '',
                'plan_name': parts[5] if len(parts) > 5 else '',
                'amount': _safe_float(parts[7]) if len(parts) > 7 else None,
            }
            result['eligibility_items'].append(eb)
            if parts[1] == '1':
                result['eligible'] = True
                if eb['plan_name']:
                    result['plan_name'] = eb['plan_name']
            elif parts[1] == 'C' and eb['amount']:
                result['deductible'] = eb['amount']
            elif parts[1] == 'G' and eb['amount']:
                result['deductible_met'] = eb['amount']
            elif parts[1] == 'A' and eb['amount']:
                result['copay'] = eb['amount']

    return result


def _safe_float(s: str) -> float:
    try:
        return float(s) if s else 0.0
    except (ValueError, TypeError):
        return 0.0
