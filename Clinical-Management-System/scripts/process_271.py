#!/usr/bin/env python3
"""
Process 271 eligibility responses and optionally update Odoo patient policies.
Usage: python3 process_271.py --url http://localhost:8069 --db odoo_clinic --user admin --password admin --dir ./edi/in/271/
"""
import argparse
import sys
import xmlrpc.client
from pathlib import Path
from datetime import datetime


def parse_271(content: str) -> dict:
    """Parse X12 271 eligibility response."""
    segments = [s.strip() for s in content.replace("\n", "").split("~") if s.strip()]
    result = {
        "control_number": "",
        "payer_name": "",
        "provider_name": "",
        "member": {},
        "eligibility": [],
    }
    current_member = {}

    for seg in segments:
        parts = seg.split("*")
        tag = parts[0]

        if tag == "ISA" and len(parts) > 13:
            result["control_number"] = parts[13]
        elif tag == "N1":
            if len(parts) > 2:
                if parts[1] == "PR":
                    result["payer_name"] = parts[2]
                elif parts[1] == "1P":
                    result["provider_name"] = parts[2]
        elif tag == "NM1" and len(parts) > 3:
            if parts[1] == "IL":  # Insured
                current_member = {
                    "last_name": parts[3] if len(parts) > 3 else "",
                    "first_name": parts[4] if len(parts) > 4 else "",
                    "member_id": parts[9] if len(parts) > 9 else "",
                }
                result["member"] = current_member
        elif tag == "EB" and len(parts) > 1:
            eb_info = {
                "eligibility_code": parts[1],
                "coverage_level": parts[2] if len(parts) > 2 else "",
                "service_type": parts[3] if len(parts) > 3 else "",
                "insurance_type": parts[4] if len(parts) > 4 else "",
                "plan_name": parts[5] if len(parts) > 5 else "",
                "amount": float(parts[7]) if len(parts) > 7 and parts[7] else None,
            }
            result["eligibility"].append(eb_info)

    # Determine overall eligibility
    result["eligible"] = any(e["eligibility_code"] == "1" for e in result["eligibility"])
    result["plan_name"] = next(
        (e["plan_name"] for e in result["eligibility"] if e.get("plan_name")), ""
    )
    result["deductible"] = next(
        (e["amount"] for e in result["eligibility"]
         if e["eligibility_code"] == "C" and e.get("amount")), None
    )
    result["deductible_met"] = next(
        (e["amount"] for e in result["eligibility"]
         if e["eligibility_code"] == "G" and e.get("amount")), None
    )
    result["copay"] = next(
        (e["amount"] for e in result["eligibility"]
         if e["eligibility_code"] == "A" and e.get("amount")), None
    )

    return result


def main():
    parser = argparse.ArgumentParser(description="Process 271 eligibility responses")
    parser.add_argument("--url", default="http://localhost:8069")
    parser.add_argument("--db", default="odoo_clinic")
    parser.add_argument("--user", default="admin")
    parser.add_argument("--password", default="admin_local_strong")
    parser.add_argument("--dir", default="./edi/in/271/")
    args = parser.parse_args()

    src_dir = Path(args.dir)
    if not src_dir.exists():
        print(f"[ERROR] Directory not found: {src_dir}")
        sys.exit(1)

    files = sorted(src_dir.glob("*.txt"))
    if not files:
        print(f"No .txt files in {src_dir}")
        sys.exit(0)

    # Try Odoo connection
    odoo_ok = False
    uid = models = None
    try:
        common = xmlrpc.client.ServerProxy(f"{args.url}/xmlrpc/2/common")
        uid = common.authenticate(args.db, args.user, args.password, {})
        models = xmlrpc.client.ServerProxy(f"{args.url}/xmlrpc/2/object")
        odoo_ok = uid > 0
        print(f"Odoo connected (uid={uid})")
    except Exception as e:
        print(f"Odoo unavailable ({e}). Printing parsed results only.")

    processed_dir = src_dir / "processed"
    processed_dir.mkdir(exist_ok=True)

    for f in files:
        content = f.read_text(encoding="utf-8")
        elig = parse_271(content)

        member = elig.get("member", {})
        member_name = f"{member.get('first_name', '')} {member.get('last_name', '')}".strip()
        print(f"\n--- {f.name} ---")
        print(f"  Member: {member_name} (ID: {member.get('member_id', 'N/A')})")
        print(f"  Payer: {elig['payer_name']} | Plan: {elig['plan_name']}")
        eligible_str = "ELIGIBLE" if elig["eligible"] else "NOT ELIGIBLE"
        print(f"  Status: {eligible_str}")
        if elig["deductible"] is not None:
            met = elig["deductible_met"] or 0.0
            print(f"  Deductible: ${elig['deductible']:.2f} (met: ${met:.2f})")
        if elig["copay"] is not None:
            print(f"  Copay: ${elig['copay']:.2f}")

        if odoo_ok:
            try:
                models.execute_kw(args.db, uid, args.password,
                                  "clinic.edi.eligibility.request", "search", [[]], {"limit": 1})
                # Find matching eligibility request by member_id
                reqs = models.execute_kw(
                    args.db, uid, args.password, "clinic.edi.eligibility.request",
                    "search_read",
                    [[["state", "=", "sent"]]],
                    {"fields": ["id", "patient_id"], "limit": 1},
                )
                if reqs:
                    req_id = reqs[0]["id"]
                    models.execute_kw(args.db, uid, args.password,
                                      "clinic.edi.eligibility.request", "write",
                                      [[req_id], {
                                          "eligible": elig["eligible"],
                                          "plan_name": elig["plan_name"],
                                          "eligibility_notes": f"Deductible: ${elig.get('deductible') or 0:.2f}",
                                          "state": "processed",
                                      }])
                    print(f"  → Updated eligibility request id={req_id}")
            except Exception as e:
                print(f"  → Odoo update skipped: {e}")

        f.rename(processed_dir / f.name)

    print(f"\n✓ Processed {len(files)} file(s). Moved to {processed_dir}")


if __name__ == "__main__":
    main()
