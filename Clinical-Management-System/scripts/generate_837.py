#!/usr/bin/env python3
"""
Generate X12 837P claim files from Odoo encounters (or mock data).
Usage: python3 generate_837.py --url http://localhost:8069 --db odoo_clinic --user admin --password admin --output ./edi/out/837/
"""
import argparse
import os
import sys
import xmlrpc.client
from datetime import datetime
from pathlib import Path


def _ts():
    return datetime.utcnow().strftime("%Y%m%d%H%M%S")


def _ctrl(n: int) -> str:
    return f"{n:09d}"


def build_837p(claim_ref: str, patient_name: str, insurer_payer_id: str,
               member_id: str, physician_npi: str, service_date: str,
               lines: list, sender_id: str = "SENDCLINIC",
               receiver_id: str = "CLRHOUSE") -> str:
    """Build a minimal but valid X12 837P transaction set."""
    now = datetime.utcnow()
    date_str = now.strftime("%y%m%d")
    time_str = now.strftime("%H%M")
    ctrl = _ctrl(int(now.timestamp()) % 999999999)

    last, first = (patient_name.split(" ", 1) + [""])[:2]

    segments = [
        f"ISA*00*          *00*          *ZZ*{sender_id:<15}*ZZ*{receiver_id:<15}*{date_str}*{time_str}*^*00501*{ctrl}*0*T*:~",
        f"GS*HC*{sender_id}*{receiver_id}*{now.strftime('%Y%m%d')}*{time_str}*1*X*005010X222A1~",
        "ST*837*0001*005010X222A1~",
        "NM1*41*2*MAIN CLINIC HQ*****46*TIN123456789~",
        "PER*IC*BILLING DEPT*TE*5551234567~",
        f"NM1*40*2*{receiver_id}*****46*987654321~",
        "HL*1**20*1~",
        "NM1*85*2*MAIN CLINIC HQ*****XX*1234567890~",
        "N3*123 MEDICAL DR~",
        "N4*NEW YORK*NY*10001~",
        "REF*EI*TIN123456789~",
        "HL*2*1*22*0~",
        "SBR*P*18*GROUP001**PPO***CI~",
        f"NM1*IL*1*{last}*{first}****MI*{member_id}~",
        f"NM1*PR*2*INSURER*****PI*{insurer_payer_id}~",
        f"CLM*{claim_ref}*{sum(l[2] for l in lines):.2f}***11:B:1*Y*A*Y*I~",
        f"DTP*434*D8*{service_date}~",
        "HI*ABK:Z00.00~",
        f"NM1*82*1*PHYSICIAN*DOC***MD*XX*{physician_npi}~",
    ]

    for i, (cpt, qty, price) in enumerate(lines, 1):
        segments += [
            f"LX*{i}~",
            f"SV1*HC:{cpt}**{price:.2f}*UN*{qty:.0f}***1~",
            f"DTP*472*D8*{service_date}~",
        ]

    seg_count = len(segments) - 2  # exclude ISA and IEA
    segments += [
        f"SE*{seg_count - 2}*0001~",  # ST + SE + body
        "GE*1*1~",
        f"IEA*1*{ctrl}~",
    ]
    return "\n".join(segments)


def generate_mock_claims(output_dir: Path):
    """Generate sample 837P files without Odoo connection."""
    claims = [
        {
            "ref": "CLAIM_DEMO_001", "patient": "Doe John", "payer": "BCBS001",
            "member_id": "INS123456", "npi": "9876543210",
            "date": datetime.utcnow().strftime("%Y%m%d"),
            "lines": [("99213", 1, 150.00), ("85025", 1, 50.00)],
        },
        {
            "ref": "CLAIM_DEMO_002", "patient": "Smith Mary", "payer": "UHC002",
            "member_id": "INS789012", "npi": "1122334455",
            "date": datetime.utcnow().strftime("%Y%m%d"),
            "lines": [("99214", 1, 200.00), ("93000", 1, 150.00)],
        },
        {
            "ref": "CLAIM_DEMO_003", "patient": "Williams James", "payer": "UHC002",
            "member_id": "INS345678", "npi": "5544332211",
            "date": datetime.utcnow().strftime("%Y%m%d"),
            "lines": [("99215", 1, 350.00)],
        },
    ]
    created = []
    for c in claims:
        content = build_837p(c["ref"], c["patient"], c["payer"], c["member_id"],
                             c["npi"], c["date"], c["lines"])
        fname = output_dir / f"837P_{c['ref']}_{_ts()}.txt"
        fname.write_text(content)
        created.append(fname)
        print(f"  Generated: {fname.name}")
    return created


def main():
    parser = argparse.ArgumentParser(description="Generate 837P EDI files")
    parser.add_argument("--url", default="http://localhost:8069")
    parser.add_argument("--db", default="odoo_clinic")
    parser.add_argument("--user", default="admin")
    parser.add_argument("--password", default="admin_local_strong")
    parser.add_argument("--output", default="./edi/out/837/")
    args = parser.parse_args()

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    # Try Odoo connection
    try:
        common = xmlrpc.client.ServerProxy(f"{args.url}/xmlrpc/2/common")
        uid = common.authenticate(args.db, args.user, args.password, {})
        if not uid:
            raise ConnectionError("Authentication failed")
        models = xmlrpc.client.ServerProxy(f"{args.url}/xmlrpc/2/object")

        # Check if clinic.billing.claim exists
        models.execute_kw(args.db, uid, args.password, "clinic.billing.claim", "search", [[]], {"limit": 1})

        print("Odoo connection OK. Fetching claims ...")
        claims = models.execute_kw(args.db, uid, args.password, "clinic.billing.claim",
                                   "search_read",
                                   [[["state", "in", ["draft", "submitted"]]]],
                                   {"fields": ["name", "patient_id", "policy_id", "service_date",
                                               "total_amount", "line_ids"], "limit": 50})
        if not claims:
            print("  No claims found in Odoo. Generating mock claims instead.")
            generate_mock_claims(output_dir)
        else:
            for claim in claims:
                content = build_837p(
                    claim_ref=claim["name"],
                    patient_name=claim.get("patient_id", ["", "Unknown"])[1],
                    insurer_payer_id="BCBS001",
                    member_id="MEMBER000",
                    physician_npi="1234567890",
                    service_date=claim.get("service_date", "").replace("-", "") or datetime.utcnow().strftime("%Y%m%d"),
                    lines=[("99213", 1, float(claim.get("total_amount", 0)))],
                )
                fname = output_dir / f"837P_{claim['name']}_{_ts()}.txt"
                fname.write_text(content)
                print(f"  Generated: {fname.name}")
    except Exception as exc:
        print(f"  Odoo connection failed ({exc}). Using mock data.")
        generate_mock_claims(output_dir)

    print(f"\n✓ 837P files written to {output_dir}")


if __name__ == "__main__":
    main()
