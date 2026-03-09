#!/usr/bin/env python3
"""
Send 837P files to mock clearinghouse via REST.
Usage: python3 send_837.py --dir ./edi/out/837/ --url http://localhost:18080
"""
import argparse
import json
import os
import sys
import urllib.request
import urllib.error
from pathlib import Path


def send_file(url: str, filepath: Path) -> dict:
    content = filepath.read_text(encoding="utf-8")
    payload = json.dumps({"content": content}).encode("utf-8")
    req = urllib.request.Request(
        f"{url}/submit-837",
        data=payload,
        headers={"Content-Type": "application/json"},
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
    args = parser.parse_args()

    src_dir = Path(args.dir)
    if not src_dir.exists():
        print(f"[ERROR] Directory not found: {src_dir}")
        sys.exit(1)

    files = sorted(src_dir.glob("*.txt"))
    if not files:
        print(f"No .txt files found in {src_dir}")
        sys.exit(0)

    print(f"Sending {len(files)} file(s) to {args.url} ...")
    ok = failed = 0
    for f in files:
        result = send_file(args.url, f)
        status = result.get("status", "unknown")
        ctrl = result.get("control_number", "")
        msg = result.get("message", "")
        if status == "accepted":
            print(f"  ✓ {f.name} → control={ctrl}")
            ok += 1
        else:
            print(f"  ✗ {f.name} → {status}: {msg}")
            failed += 1

    print(f"\nDone: {ok} sent, {failed} failed")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
