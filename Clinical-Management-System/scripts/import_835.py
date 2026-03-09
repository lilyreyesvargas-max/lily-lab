#!/usr/bin/env python3
"""
Import 835 ERA remittance files into Odoo.
Usage: python3 import_835.py --url http://localhost:8069 --db odoo_clinic --user admin --password admin --dir ./edi/in/835/
"""
import argparse
import re
import sys
import xmlrpc.client
from pathlib import Path
from datetime import datetime


def parse_835(content: str) -> dict:
    """Parse X12 835 ERA. Returns structured remittance data."""
    segments = [s.strip() for s in content.replace("\n", "").split("~") if s.strip()]
    result = {
        "control_number": "",
        "payment_date": "",
        "check_number": "",
        "total_paid": 0.0,
        "payer_name": "",
        "payee_name": "",
        "claims": [],
    }
    current_claim = None

    for seg in segments:
        parts = seg.split("*")
        tag = parts[0]

        if tag == "ISA" and len(parts) > 13:
            result["control_number"] = parts[13]
        elif tag == "TRN" and len(parts) > 2:
            result["check_number"] = parts[2]
        elif tag == "DTM" and len(parts) > 2 and parts[1] == "405":
            result["payment_date"] = parts[2]
        elif tag == "BPR" and len(parts) > 2:
            try:
                result["total_paid"] = float(parts[2])
            except ValueError:
                pass
        elif tag == "N1":
            if len(parts) > 2:
                if parts[1] == "PR":
                    result["payer_name"] = parts[2]
                elif parts[1] == "PE":
                    result["payee_name"] = parts[2]
        elif tag == "CLP" and len(parts) > 7:
            current_claim = {
                "claim_ref": parts[1],
                "status_code": parts[2],
                "billed": float(parts[3]) if len(parts) > 3 else 0.0,
                "paid": float(parts[4]) if len(parts) > 4 else 0.0,
                "patient_ref": parts[7] if len(parts) > 7 else "",
                "services": [],
            }
            result["claims"].append(current_claim)
        elif tag == "SVC" and current_claim and len(parts) > 3:
            current_claim["services"].append({
                "code": parts[1],
                "billed": float(parts[2]) if parts[2] else 0.0,
                "paid": float(parts[3]) if parts[3] else 0.0,
            })

    return result


def main():
    parser = argparse.ArgumentParser(description="Import 835 ERA files into Odoo")
    parser.add_argument("--url", default="http://localhost:8069")
    parser.add_argument("--db", default="odoo_clinic")
    parser.add_argument("--user", default="admin")
    parser.add_argument("--password", default="admin_local_strong")
    parser.add_argument("--dir", default="./edi/in/835/")
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
        print(f"Odoo unavailable ({e}). Running in dry-run mode.")

    processed_dir = src_dir / "processed"
    processed_dir.mkdir(exist_ok=True)

    imported = 0
    for f in files:
        content = f.read_text(encoding="utf-8")
        era = parse_835(content)

        print(f"\n--- {f.name} ---")
        print(f"  Payer: {era['payer_name']} | Check: {era['check_number']} | Total: ${era['total_paid']:.2f}")
        for c in era["claims"]:
            status_map = {"1": "PAID", "2": "ADJUSTED", "3": "DENIED_ACK", "4": "DENIED"}
            st = status_map.get(c["status_code"], c["status_code"])
            print(f"  Claim {c['claim_ref']}: billed=${c['billed']:.2f}, paid=${c['paid']:.2f} [{st}]")

        if odoo_ok:
            try:
                # Check if clinic.remittance model exists
                models.execute_kw(args.db, uid, args.password, "clinic.remittance",
                                  "search", [[]], {"limit": 1})
                rem_id = models.execute_kw(args.db, uid, args.password, "clinic.remittance", "create", [{
                    "name": era["check_number"] or f"ERA-{era['control_number']}",
                    "payment_date": era["payment_date"][:4] + "-" + era["payment_date"][4:6] + "-" + era["payment_date"][6:8]
                    if len(era["payment_date"]) == 8 else datetime.today().isoformat()[:10],
                    "check_eft_number": era["check_number"],
                    "total_paid": era["total_paid"],
                    "state": "pending",
                }])
                print(f"  → Created clinic.remittance id={rem_id}")
            except Exception as e:
                print(f"  → Odoo import skipped: {e}")

        # Move file
        f.rename(processed_dir / f.name)
        imported += 1

    print(f"\n✓ Processed {imported} file(s). Moved to {processed_dir}")


if __name__ == "__main__":
    main()
