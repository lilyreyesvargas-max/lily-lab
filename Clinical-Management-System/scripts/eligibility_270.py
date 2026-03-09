#!/usr/bin/env python3
"""
Send 270 eligibility request to mock clearinghouse and save 271 response.
Usage: python3 eligibility_270.py --url http://localhost:18080 --input ./edi/samples/sample_270_request.txt --output ./edi/out/270/
"""
import argparse
import json
import os
import sys
import urllib.request
import urllib.error
from datetime import datetime
from pathlib import Path


def send_270(url: str, content: str) -> dict:
    payload = json.dumps({"content": content}).encode("utf-8")
    req = urllib.request.Request(
        f"{url}/eligibility/270",
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        return {"status": "error", "message": f"HTTP {e.code}: {body}"}
    except urllib.error.URLError as e:
        return {"status": "error", "message": str(e.reason)}


def main():
    parser = argparse.ArgumentParser(description="Send 270 eligibility request")
    parser.add_argument("--url", default=os.getenv("EDI_BASE_URL", "http://localhost:18080"))
    parser.add_argument("--input", default="./edi/samples/sample_270_request.txt")
    parser.add_argument("--output", default="./edi/out/270/")
    args = parser.parse_args()

    input_path = Path(args.input)
    if not input_path.exists():
        print(f"[ERROR] Input file not found: {input_path}")
        sys.exit(1)

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    content = input_path.read_text(encoding="utf-8")
    print(f"Sending 270 from {input_path.name} to {args.url} ...")

    result = send_270(args.url, content)
    status = result.get("status", "unknown")

    if status == "ok":
        ts = datetime.utcnow().strftime("%Y%m%d_%H%M%S")
        out_file = output_dir / f"270_sent_{ts}.txt"
        out_file.write_text(content)

        resp_271 = result.get("content", "")
        if resp_271:
            in_271_dir = Path("./edi/in/271")
            in_271_dir.mkdir(parents=True, exist_ok=True)
            resp_file = in_271_dir / f"271_response_{ts}.txt"
            resp_file.write_text(resp_271)
            print(f"  ✓ 271 response saved to {resp_file}")

        print(f"  Eligible: {result.get('eligible')}")
        print(f"  Plan: {result.get('plan')}")
        print(f"  Deductible: ${result.get('deductible', 0):.2f} (met: ${result.get('deductible_met', 0):.2f})")
        print(f"  Copay: ${result.get('copay', 0):.2f}")
    else:
        print(f"  ✗ Error: {result.get('message')}")
        sys.exit(1)

    print("\n✓ 270/271 eligibility exchange complete")


if __name__ == "__main__":
    main()
