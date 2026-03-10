#!/usr/bin/env python3
"""
Send 837P files to mock clearinghouse via REST.
Usage: python3 send_837.py --dir ./edi/out/837/ --url http://localhost:18080
"""
import argparse
import json
import os
import sys
import xmlrpc.client
import urllib.request
import urllib.error
from pathlib import Path


def send_file(url: str, filepath: Path) -> dict:
    import email.mime.multipart
    import uuid
    content = filepath.read_text(encoding="utf-8")
    boundary = uuid.uuid4().hex
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{filepath.name}"\r\n'
        f"Content-Type: text/plain\r\n\r\n"
        f"{content}\r\n"
        f"--{boundary}--\r\n"
    ).encode("utf-8")
    req = urllib.request.Request(
        f"{url}/submit-837",
        data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        return {"status": "error", "message": str(e), "code": e.code}
    except urllib.error.URLError as e:
        return {"status": "error", "message": str(e.reason)}


def main():
    parser = argparse.ArgumentParser(description="Send 837P files to clearinghouse")
    parser.add_argument("--dir", default="./edi/out/837/")
    parser.add_argument("--url", default=os.getenv("EDI_BASE_URL", "http://localhost:18080"))
    parser.add_argument("--odoo-url", default="http://localhost:8070")
    parser.add_argument("--db", default="odoo_clinic")
    parser.add_argument("--user", default="admin@clinic.local")
    parser.add_argument("--password", default="admin_local_strong")
    args = parser.parse_args()

    src_dir = Path(args.dir)
    if not src_dir.exists():
        print(f"[ERROR] Directory not found: {src_dir}")
        sys.exit(1)

    files = sorted(src_dir.glob("*.txt"))
    if not files:
        print(f"No .txt files found in {src_dir}")
        sys.exit(0)

    # Odoo connection for EDI transaction logging
    odoo_ok = False
    uid = odoo_models = None
    try:
        common = xmlrpc.client.ServerProxy(f"{args.odoo_url}/xmlrpc/2/common")
        uid = common.authenticate(args.db, args.user, args.password, {})
        odoo_models = xmlrpc.client.ServerProxy(f"{args.odoo_url}/xmlrpc/2/object")
        odoo_ok = uid and uid > 0
        if odoo_ok:
            print(f"Odoo connected (uid={uid})")
    except Exception as e:
        print(f"Odoo unavailable ({e}). Transactions will not be logged.")

    print(f"Sending {len(files)} file(s) to {args.url} ...")
    ok = failed = 0
    for f in files:
        content = f.read_text(encoding="utf-8")
        result = send_file(args.url, f)
        status = result.get("status", "unknown")
        ctrl = result.get("control_number", "")
        msg = result.get("message", "")
        edi_state = "sent" if status == "accepted" else "error"

        if status == "accepted":
            print(f"  ✓ {f.name} → control={ctrl}")
            ok += 1
        else:
            print(f"  ✗ {f.name} → {status}: {msg}")
            failed += 1

        if odoo_ok:
            try:
                txn_id = odoo_models.execute_kw(args.db, uid, args.password,
                    "clinic.edi.transaction", "create", [{
                        "transaction_type": "837",
                        "direction": "outbound",
                        "content": content,
                        "control_number": ctrl or "",
                        "state": edi_state,
                        "validation_errors": msg if edi_state == "error" else False,
                    }])
                print(f"    → EDI transaction id={txn_id} [{edi_state}]")
            except Exception as e:
                print(f"    → EDI log skipped: {e}")

    print(f"\nDone: {ok} sent, {failed} failed")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
