#!/usr/bin/env python3
"""
Load demo data into Odoo Clinical Management System via XML-RPC.
Usage: python3 load_demo_data.py --url http://localhost:8069 --db odoo_clinic --user admin --password admin_local_strong
"""
import argparse
import random
import sys
import xmlrpc.client
from datetime import date, timedelta

# ── Sample data ───────────────────────────────────────────────────────────────
FIRST_NAMES = [
    "John", "Jane", "Robert", "Maria", "James", "Patricia", "Michael", "Linda",
    "William", "Barbara", "David", "Elizabeth", "Richard", "Jennifer", "Joseph",
    "Susan", "Thomas", "Jessica", "Charles", "Sarah", "Christopher", "Karen",
    "Daniel", "Lisa", "Matthew", "Nancy", "Anthony", "Betty", "Mark", "Margaret",
    "Donald", "Sandra", "Steven", "Ashley", "Paul", "Dorothy", "Andrew", "Kimberly",
    "Kenneth", "Emily",
]
LAST_NAMES = [
    "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
    "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson",
    "Thomas", "Taylor", "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson",
    "White", "Harris", "Sanchez", "Clark", "Ramirez", "Lewis", "Robinson",
]
BLOOD_TYPES = ["A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"]
GENDERS = ["male", "female"]


def connect(url: str, db: str, user: str, password: str):
    common = xmlrpc.client.ServerProxy(f"{url}/xmlrpc/2/common")
    uid = common.authenticate(db, user, password, {})
    if not uid:
        print(f"[ERROR] Authentication failed for user '{user}' on db '{db}'")
        sys.exit(1)
    models = xmlrpc.client.ServerProxy(f"{url}/xmlrpc/2/object")
    return uid, models


def model_exists(models, uid, db, password, model_name: str) -> bool:
    try:
        models.execute_kw(db, uid, password, model_name, "search", [[]], {"limit": 1})
        return True
    except Exception:
        return False


def create_or_get(models, uid, db, password, model, domain, values):
    existing = models.execute_kw(db, uid, password, model, "search", [domain], {"limit": 1})
    if existing:
        return existing[0]
    return models.execute_kw(db, uid, password, model, "create", [values])


def main():
    parser = argparse.ArgumentParser(description="Load Clinic demo data into Odoo")
    parser.add_argument("--url", default="http://localhost:8069")
    parser.add_argument("--db", default="odoo_clinic")
    parser.add_argument("--user", default="admin")
    parser.add_argument("--password", default="admin_local_strong")
    args = parser.parse_args()

    print(f"Connecting to {args.url} db={args.db} user={args.user} ...")
    uid, models = connect(args.url, args.db, args.user, args.password)
    print(f"  Connected. UID={uid}")

    # ── Companies ─────────────────────────────────────────────────────────────
    print("\n[1/4] Creating companies ...")
    companies = [
        {"name": "Main Clinic HQ", "street": "123 Medical Dr", "city": "New York", "zip": "10001", "country_id": False},
        {"name": "Clinic Branch S1", "street": "456 Health Ave", "city": "Brooklyn", "zip": "11201", "country_id": False},
        {"name": "Clinic Branch S2", "street": "789 Wellness Rd", "city": "Queens", "zip": "11101", "country_id": False},
        {"name": "Clinic Branch S3", "street": "321 Care Blvd", "city": "Bronx", "zip": "10451", "country_id": False},
    ]
    company_ids = []
    for c in companies:
        cid = create_or_get(models, uid, args.db, args.password, "res.company",
                            [["name", "=", c["name"]]], c)
        company_ids.append(cid)
        print(f"  Company '{c['name']}' → id={cid}")

    # ── Patients ──────────────────────────────────────────────────────────────
    print("\n[2/4] Creating 40 patients ...")
    if not model_exists(models, uid, args.db, args.password, "clinic.patient"):
        print("  [SKIP] clinic.patient model not found — install clinic_patients first")
    else:
        random.seed(42)
        patient_count = 0
        for i in range(40):
            fn = random.choice(FIRST_NAMES)
            ln = random.choice(LAST_NAMES)
            dob = date(1950, 1, 1) + timedelta(days=random.randint(0, 25000))
            cid = random.choice(company_ids)
            vals = {
                "name": f"{fn} {ln}",
                "date_of_birth": dob.isoformat(),
                "gender": random.choice(GENDERS),
                "blood_type": random.choice(BLOOD_TYPES),
                "company_id": cid,
                "phone": f"555-{random.randint(1000,9999)}",
                "email": f"{fn.lower()}.{ln.lower()}{i}@example.com",
            }
            pid = models.execute_kw(args.db, uid, args.password, "clinic.patient", "create", [vals])
            patient_count += 1
        print(f"  Created {patient_count} patients")

    # ── Insurers ──────────────────────────────────────────────────────────────
    print("\n[3/4] Creating insurers ...")
    if not model_exists(models, uid, args.db, args.password, "clinic.insurer"):
        print("  [SKIP] clinic.insurer model not found")
    else:
        insurers = [
            {"name": "Blue Cross PPO", "code": "BCBS", "payer_id": "BCBS001"},
            {"name": "United Health HMO", "code": "UHC", "payer_id": "UHC002"},
            {"name": "Aetna PPO", "code": "AETNA", "payer_id": "AETNA003"},
            {"name": "Cigna EPO", "code": "CIGNA", "payer_id": "CIGNA004"},
            {"name": "Medicaid NY", "code": "MCAID", "payer_id": "MCAID005"},
            {"name": "Medicare Part B", "code": "MCARE", "payer_id": "MCARE006"},
        ]
        for ins in insurers:
            iid = create_or_get(models, uid, args.db, args.password, "clinic.insurer",
                                [["code", "=", ins["code"]]], ins)
            print(f"  Insurer '{ins['name']}' → id={iid}")

    # ── Employees / Doctors ───────────────────────────────────────────────────
    print("\n[4/4] Creating employees ...")
    doctor_names = [
        ("Jane", "Smith", "Medicina General"), ("Carlos", "Rodriguez", "Ginecología"),
        ("Emily", "Chen", "Oftalmología"), ("Marcus", "Williams", "Estomatología"),
        ("Ana", "Garcia", "Medicina General"), ("Robert", "Johnson", "Ginecología"),
        ("Lisa", "Park", "Oftalmología"), ("David", "Martinez", "Estomatología"),
        ("Sarah", "Kim", "Medicina General"), ("Michael", "Brown", "Ginecología"),
        ("Jennifer", "Davis", "Oftalmología"), ("Thomas", "Wilson", "Estomatología"),
    ]
    emp_count = 0
    for fn, ln, spec in doctor_names:
        emp_vals = {
            "name": f"Dr. {fn} {ln}",
            "job_title": f"{spec} Physician",
            "work_email": f"{fn.lower()}.{ln.lower()}@clinic.local",
        }
        eid = create_or_get(models, uid, args.db, args.password, "hr.employee",
                            [["name", "=", emp_vals["name"]]], emp_vals)
        emp_count += 1
    print(f"  Created {emp_count} doctors")

    print("\n✓ Demo data loaded successfully!")
    print(f"  Open {args.url}/web to access Odoo")


if __name__ == "__main__":
    main()
