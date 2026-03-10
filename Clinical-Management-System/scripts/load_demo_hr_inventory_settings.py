#!/usr/bin/env python3
"""
Demo data loader for HR, Inventory and Clinic Settings modules.
Usage: python3 load_demo_hr_inventory_settings.py \
         --url http://localhost:8070 --db odoo_clinic \
         --user admin@clinic.local --password admin_local_strong
"""
import argparse
import sys
import xmlrpc.client
from datetime import date, timedelta
import random

# ─── Connection ───────────────────────────────────────────────────────────────

def connect(url, db, user, password):
    common = xmlrpc.client.ServerProxy(f"{url}/xmlrpc/2/common")
    uid = common.authenticate(db, user, password, {})
    if not uid:
        print(f"[ERROR] Authentication failed for {user}")
        sys.exit(1)
    models = xmlrpc.client.ServerProxy(f"{url}/xmlrpc/2/object")
    print(f"[OK] Connected as uid={uid}")
    return uid, models


def search_or_create(models, db, uid, pwd, model, domain, vals):
    ids = models.execute_kw(db, uid, pwd, model, "search", [domain], {"limit": 1})
    if ids:
        return ids[0]
    return models.execute_kw(db, uid, pwd, model, "create", [vals])


def get_ref(models, db, uid, pwd, xml_id):
    """Resolve external XML id to record id."""
    try:
        res = models.execute_kw(db, uid, pwd, "ir.model.data", "search_read",
            [[("complete_name", "=", xml_id)]], {"fields": ["res_id"], "limit": 1})
        return res[0]["res_id"] if res else None
    except Exception:
        return None


# ─── 1. CLINIC SETTINGS ───────────────────────────────────────────────────────

def load_clinic_settings(models, db, uid, pwd):
    print("\n=== CLINIC SETTINGS ===")

    # Get companies created by load_demo_data.py
    companies = models.execute_kw(db, uid, pwd, "res.company", "search_read",
        [[("name", "in", [
            "Main Clinic HQ", "Clinic Branch S1",
            "Clinic Branch S2", "Clinic Branch S3"
        ])]],
        {"fields": ["id", "name"]})

    if not companies:
        print("  [WARN] Companies not found — run load_demo_data.py first")
        companies = models.execute_kw(db, uid, pwd, "res.company", "search_read",
            [[]], {"fields": ["id", "name"], "limit": 4})

    # Get specialties
    specialties = models.execute_kw(db, uid, pwd, "clinic.specialty", "search_read",
        [[("active", "=", True)]], {"fields": ["id", "name"]})
    spec_ids = [s["id"] for s in specialties]
    print(f"  Found {len(specialties)} specialties, {len(companies)} companies")

    codes = {
        "Main Clinic HQ":  "HQ-001",
        "Clinic Branch S1": "S1-001",
        "Clinic Branch S2": "S2-001",
        "Clinic Branch S3": "S3-001",
    }
    notes = {
        "Main Clinic HQ":  "Main headquarters — all specialties available",
        "Clinic Branch S1": "Brooklyn branch — Medicina General & Ginecología",
        "Clinic Branch S2": "Queens branch — Oftalmología & Estomatología",
        "Clinic Branch S3": "Bronx branch — Medicina General & Estomatología",
    }
    branch_specs = {
        "Main Clinic HQ":  spec_ids,
        "Clinic Branch S1": spec_ids[:2] if len(spec_ids) >= 2 else spec_ids,
        "Clinic Branch S2": spec_ids[2:4] if len(spec_ids) >= 4 else spec_ids,
        "Clinic Branch S3": [spec_ids[0], spec_ids[-1]] if len(spec_ids) >= 2 else spec_ids,
    }

    for comp in companies:
        cname = comp["name"]
        # Check if clinic.config exists for this company
        existing = models.execute_kw(db, uid, pwd, "clinic.config", "search",
            [[("company_id", "=", comp["id"])]], {"limit": 1})
        sids = branch_specs.get(cname, spec_ids)
        vals = {
            "company_id": comp["id"],
            "clinic_code": codes.get(cname, "CLI-000"),
            "specialty_ids": [[6, 0, sids]],
            "notes": notes.get(cname, ""),
        }
        if existing:
            models.execute_kw(db, uid, pwd, "clinic.config", "write",
                [existing, vals])
            print(f"  [UPDATE] clinic.config for {cname} (id={existing[0]})")
        else:
            new_id = models.execute_kw(db, uid, pwd, "clinic.config", "create", [vals])
            print(f"  [CREATE] clinic.config for {cname} → id={new_id}")


# ─── 2. HR — SHIFTS ───────────────────────────────────────────────────────────

def load_hr_shifts(models, db, uid, pwd):
    print("\n=== HR — SHIFT DAYS ===")

    days_data = [
        ("Monday",    "MON", 1),
        ("Tuesday",   "TUE", 2),
        ("Wednesday", "WED", 3),
        ("Thursday",  "THU", 4),
        ("Friday",    "FRI", 5),
        ("Saturday",  "SAT", 6),
        ("Sunday",    "SUN", 7),
    ]
    day_ids = {}
    for name, code, seq in days_data:
        did = search_or_create(models, db, uid, pwd,
            "clinic.shift.day",
            [("code", "=", code)],
            {"name": name, "code": code, "sequence": seq})
        day_ids[code] = did
        print(f"  [OK] shift.day {name} → id={did}")

    print("\n=== HR — SHIFTS ===")
    company_id = models.execute_kw(db, uid, pwd, "res.company", "search",
        [[]], {"limit": 1})[0]

    shifts_data = [
        {
            "name": "Morning Shift",
            "hour_from": 7.0, "hour_to": 15.0,
            "day_ids": [[6, 0, [day_ids[d] for d in ["MON","TUE","WED","THU","FRI"]]]],
        },
        {
            "name": "Afternoon Shift",
            "hour_from": 15.0, "hour_to": 23.0,
            "day_ids": [[6, 0, [day_ids[d] for d in ["MON","TUE","WED","THU","FRI"]]]],
        },
        {
            "name": "Night Shift",
            "hour_from": 23.0, "hour_to": 7.0,
            "day_ids": [[6, 0, [day_ids[d] for d in ["MON","TUE","WED","THU","FRI"]]]],
        },
        {
            "name": "Weekend Shift",
            "hour_from": 8.0, "hour_to": 16.0,
            "day_ids": [[6, 0, [day_ids[d] for d in ["SAT","SUN"]]]],
        },
    ]
    for s in shifts_data:
        s["company_id"] = company_id
        sid = search_or_create(models, db, uid, pwd,
            "clinic.shift",
            [("name", "=", s["name"])],
            s)
        print(f"  [OK] shift '{s['name']}' → id={sid}")


# ─── 3. HR — EMPLOYEES ───────────────────────────────────────────────────────

def load_hr_employees(models, db, uid, pwd):
    print("\n=== HR — EMPLOYEES ===")

    # Get companies
    companies = models.execute_kw(db, uid, pwd, "res.company", "search_read",
        [[("name", "in", ["Main Clinic HQ","Clinic Branch S1","Clinic Branch S2","Clinic Branch S3"])]],
        {"fields": ["id", "name"]})
    comp_map = {c["name"]: c["id"] for c in companies}

    # Get specialties
    specs = models.execute_kw(db, uid, pwd, "clinic.specialty", "search_read",
        [[]], {"fields": ["id", "name"]})
    spec_map = {s["name"]: s["id"] for s in specs}

    # Get or create departments
    departments = {}
    for dept_name in ["Medical", "Nursing", "Administration", "Reception"]:
        did = search_or_create(models, db, uid, pwd,
            "hr.department",
            [("name", "=", dept_name)],
            {"name": dept_name})
        departments[dept_name] = did

    employees = [
        # Doctors — Main HQ
        {"name": "Dr. Carlos Mendoza",   "role": "doctor",       "specialty": "Medicina General",
         "license": "MD-001-NY", "company": "Main Clinic HQ",    "dept": "Medical",
         "job": "General Physician",     "email": "c.mendoza@clinic.local"},
        {"name": "Dr. Ana Gutiérrez",    "role": "doctor",       "specialty": "Ginecología",
         "license": "MD-002-NY", "company": "Main Clinic HQ",    "dept": "Medical",
         "job": "Gynecologist",          "email": "a.gutierrez@clinic.local"},
        {"name": "Dr. Roberto Silva",    "role": "doctor",       "specialty": "Oftalmología",
         "license": "MD-003-NY", "company": "Main Clinic HQ",    "dept": "Medical",
         "job": "Ophthalmologist",       "email": "r.silva@clinic.local"},
        {"name": "Dr. María Torres",     "role": "doctor",       "specialty": "Estomatología",
         "license": "MD-004-NY", "company": "Main Clinic HQ",    "dept": "Medical",
         "job": "Dentist",               "email": "m.torres@clinic.local"},
        # Doctors — Branch S1
        {"name": "Dr. Luis Ramírez",     "role": "doctor",       "specialty": "Medicina General",
         "license": "MD-005-NY", "company": "Clinic Branch S1",  "dept": "Medical",
         "job": "General Physician",     "email": "l.ramirez@clinic.local"},
        {"name": "Dr. Sofía Castro",     "role": "doctor",       "specialty": "Ginecología",
         "license": "MD-006-NY", "company": "Clinic Branch S1",  "dept": "Medical",
         "job": "Gynecologist",          "email": "s.castro@clinic.local"},
        # Doctors — Branch S2
        {"name": "Dr. Juan Morales",     "role": "doctor",       "specialty": "Oftalmología",
         "license": "MD-007-NY", "company": "Clinic Branch S2",  "dept": "Medical",
         "job": "Ophthalmologist",       "email": "j.morales@clinic.local"},
        {"name": "Dr. Elena Vega",       "role": "doctor",       "specialty": "Estomatología",
         "license": "MD-008-NY", "company": "Clinic Branch S2",  "dept": "Medical",
         "job": "Dentist",               "email": "e.vega@clinic.local"},
        # Doctors — Branch S3
        {"name": "Dr. Pedro Flores",     "role": "doctor",       "specialty": "Medicina General",
         "license": "MD-009-NY", "company": "Clinic Branch S3",  "dept": "Medical",
         "job": "General Physician",     "email": "p.flores@clinic.local"},
        {"name": "Dr. Carmen Ruiz",      "role": "doctor",       "specialty": "Estomatología",
         "license": "MD-010-NY", "company": "Clinic Branch S3",  "dept": "Medical",
         "job": "Dentist",               "email": "c.ruiz@clinic.local"},
        # Nurses
        {"name": "Enf. Patricia López",  "role": "nurse",        "specialty": "Medicina General",
         "license": "RN-001-NY", "company": "Main Clinic HQ",    "dept": "Nursing",
         "job": "Registered Nurse",      "email": "p.lopez@clinic.local"},
        {"name": "Enf. Diego Hernández", "role": "nurse",        "specialty": "Ginecología",
         "license": "RN-002-NY", "company": "Main Clinic HQ",    "dept": "Nursing",
         "job": "Registered Nurse",      "email": "d.hernandez@clinic.local"},
        {"name": "Enf. Isabel Jiménez",  "role": "nurse",        "specialty": "Medicina General",
         "license": "RN-003-NY", "company": "Clinic Branch S1",  "dept": "Nursing",
         "job": "Registered Nurse",      "email": "i.jimenez@clinic.local"},
        {"name": "Enf. Miguel Ángel García", "role": "nurse",    "specialty": "Oftalmología",
         "license": "RN-004-NY", "company": "Clinic Branch S2",  "dept": "Nursing",
         "job": "Registered Nurse",      "email": "ma.garcia@clinic.local"},
        # Receptionists
        {"name": "Rec. Laura Martínez",  "role": "receptionist", "specialty": None,
         "license": None,        "company": "Main Clinic HQ",    "dept": "Reception",
         "job": "Receptionist",          "email": "la.martinez@clinic.local"},
        {"name": "Rec. Andrés Sánchez",  "role": "receptionist", "specialty": None,
         "license": None,        "company": "Clinic Branch S1",  "dept": "Reception",
         "job": "Receptionist",          "email": "an.sanchez@clinic.local"},
        {"name": "Rec. Verónica Díaz",   "role": "receptionist", "specialty": None,
         "license": None,        "company": "Clinic Branch S2",  "dept": "Reception",
         "job": "Receptionist",          "email": "ve.diaz@clinic.local"},
        {"name": "Rec. Fernando Reyes",  "role": "receptionist", "specialty": None,
         "license": None,        "company": "Clinic Branch S3",  "dept": "Reception",
         "job": "Receptionist",          "email": "fe.reyes@clinic.local"},
        # Admin
        {"name": "Adm. Gloria Pérez",    "role": "admin",        "specialty": None,
         "license": None,        "company": "Main Clinic HQ",    "dept": "Administration",
         "job": "HR Administrator",      "email": "gl.perez@clinic.local"},
        {"name": "Adm. Ricardo Vargas",  "role": "admin",        "specialty": None,
         "license": None,        "company": "Main Clinic HQ",    "dept": "Administration",
         "job": "Billing Administrator", "email": "ri.vargas@clinic.local"},
    ]

    expiry = (date.today() + timedelta(days=730)).isoformat()  # 2 years

    created = 0
    for emp in employees:
        company_id = comp_map.get(emp["company"])
        if not company_id:
            # fallback to any company
            company_id = models.execute_kw(db, uid, pwd, "res.company", "search",
                [[]], {"limit": 1})[0]

        vals = {
            "name": emp["name"],
            "job_title": emp["job"],
            "work_email": emp["email"],
            "clinic_role": emp["role"],
            "company_id": company_id,
            "department_id": departments.get(emp["dept"]),
        }
        if emp["specialty"] and emp["specialty"] in spec_map:
            vals["specialty_id"] = spec_map[emp["specialty"]]
        if emp["license"]:
            vals["license_number"] = emp["license"]
            vals["license_expiry"] = expiry

        eid = search_or_create(models, db, uid, pwd,
            "hr.employee",
            [("name", "=", emp["name"])],
            vals)
        print(f"  [OK] {emp['role']:15s} {emp['name']} → id={eid}")
        created += 1

    print(f"  Total: {created} employees processed")


# ─── 4. INVENTORY — PRODUCTS ─────────────────────────────────────────────────

def load_inventory_products(models, db, uid, pwd):
    print("\n=== INVENTORY — PRODUCT CATEGORIES ===")

    cat_medical = search_or_create(models, db, uid, pwd,
        "product.category",
        [("name", "=", "Medical Supplies")],
        {"name": "Medical Supplies"})
    cat_consumable = search_or_create(models, db, uid, pwd,
        "product.category",
        [("name", "=", "Consumables")],
        {"name": "Consumables", "parent_id": cat_medical})
    cat_medication = search_or_create(models, db, uid, pwd,
        "product.category",
        [("name", "=", "Medications")],
        {"name": "Medications", "parent_id": cat_medical})
    cat_equipment = search_or_create(models, db, uid, pwd,
        "product.category",
        [("name", "=", "Equipment")],
        {"name": "Equipment", "parent_id": cat_medical})
    print(f"  [OK] Categories: Medical Supplies, Consumables, Medications, Equipment")

    print("\n=== INVENTORY — PRODUCTS ===")

    # Get UoM ids
    uom_unit = models.execute_kw(db, uid, pwd, "uom.uom", "search",
        [[("name", "=", "Units")]], {"limit": 1})
    uom_unit = uom_unit[0] if uom_unit else None

    uom_box = search_or_create(models, db, uid, pwd,
        "uom.uom",
        [("name", "=", "Box")],
        {"name": "Box", "category_id": models.execute_kw(
            db, uid, pwd, "uom.category", "search",
            [[("name", "=", "Unit")]], {"limit": 1})[0] if
            models.execute_kw(db, uid, pwd, "uom.category", "search",
            [[("name", "=", "Unit")]], {"limit": 1}) else 1})

    products = [
        # Consumables
        {"name": "Surgical Gloves (Box 100)",   "ref": "INS-001", "cat": cat_consumable, "type": "consumable", "price": 12.50},
        {"name": "Face Masks (Box 50)",          "ref": "INS-002", "cat": cat_consumable, "type": "consumable", "price": 8.00},
        {"name": "Sterile Gauze 4x4",           "ref": "INS-003", "cat": cat_consumable, "type": "consumable", "price": 5.75},
        {"name": "Adhesive Bandages (Box 100)",  "ref": "INS-004", "cat": cat_consumable, "type": "consumable", "price": 6.50},
        {"name": "Syringes 5ml (Box 100)",       "ref": "INS-005", "cat": cat_consumable, "type": "consumable", "price": 15.00},
        {"name": "Syringes 10ml (Box 50)",       "ref": "INS-006", "cat": cat_consumable, "type": "consumable", "price": 14.00},
        {"name": "Alcohol Swabs (Box 200)",      "ref": "INS-007", "cat": cat_consumable, "type": "consumable", "price": 4.25},
        {"name": "Cotton Rolls 500g",            "ref": "INS-008", "cat": cat_consumable, "type": "consumable", "price": 3.50},
        {"name": "Tongue Depressors (Box 100)",  "ref": "INS-009", "cat": cat_consumable, "type": "consumable", "price": 4.00},
        {"name": "Disposable Drapes",            "ref": "INS-010", "cat": cat_consumable, "type": "consumable", "price": 18.00},
        # Medications
        {"name": "Ibuprofen 400mg (Box 30)",     "ref": "MED-001", "cat": cat_medication, "type": "consumable", "price": 9.00},
        {"name": "Amoxicillin 500mg (Box 21)",   "ref": "MED-002", "cat": cat_medication, "type": "consumable", "price": 12.00},
        {"name": "Paracetamol 500mg (Box 24)",   "ref": "MED-003", "cat": cat_medication, "type": "consumable", "price": 5.00},
        {"name": "Lidocaine 2% 50ml",            "ref": "MED-004", "cat": cat_medication, "type": "consumable", "price": 22.00},
        {"name": "Saline Solution 500ml",        "ref": "MED-005", "cat": cat_medication, "type": "consumable", "price": 7.50},
        {"name": "Hydrogen Peroxide 1L",         "ref": "MED-006", "cat": cat_medication, "type": "consumable", "price": 6.00},
        {"name": "Eye Drops (Artificial Tears)", "ref": "MED-007", "cat": cat_medication, "type": "consumable", "price": 14.50},
        {"name": "Antifungal Cream 30g",         "ref": "MED-008", "cat": cat_medication, "type": "consumable", "price": 11.00},
        # Equipment
        {"name": "Disposable Thermometer",       "ref": "EQP-001", "cat": cat_equipment,  "type": "product",    "price": 35.00},
        {"name": "Blood Pressure Cuff",          "ref": "EQP-002", "cat": cat_equipment,  "type": "product",    "price": 85.00},
        {"name": "Pulse Oximeter",               "ref": "EQP-003", "cat": cat_equipment,  "type": "product",    "price": 45.00},
        {"name": "Otoscope Disposable Tip",      "ref": "EQP-004", "cat": cat_equipment,  "type": "consumable", "price": 8.00},
        {"name": "Dental Probe Set",             "ref": "EQP-005", "cat": cat_equipment,  "type": "product",    "price": 120.00},
        {"name": "Ophthalmoscope Lens",          "ref": "EQP-006", "cat": cat_equipment,  "type": "product",    "price": 95.00},
    ]

    product_ids = []
    for p in products:
        vals = {
            "name": p["name"],
            "default_code": p["ref"],
            "categ_id": p["cat"],
            "type": p["type"],
            "standard_price": p["price"],
            "list_price": p["price"] * 1.2,
        }
        if uom_unit:
            vals["uom_id"] = uom_unit
            vals["uom_po_id"] = uom_unit
        pid = search_or_create(models, db, uid, pwd,
            "product.product",
            [("default_code", "=", p["ref"])],
            vals)
        product_ids.append(pid)
        print(f"  [OK] product '{p['name']}' → id={pid}")

    return product_ids


# ─── 5. INVENTORY — SUPPLY REQUESTS ──────────────────────────────────────────

def load_supply_requests(models, db, uid, pwd, product_ids):
    print("\n=== INVENTORY — SUPPLY REQUESTS ===")

    companies = models.execute_kw(db, uid, pwd, "res.company", "search_read",
        [[("name", "in", ["Main Clinic HQ","Clinic Branch S1","Clinic Branch S2","Clinic Branch S3"])]],
        {"fields": ["id", "name"]})

    if not companies:
        companies = models.execute_kw(db, uid, pwd, "res.company", "search_read",
            [[]], {"fields": ["id", "name"], "limit": 4})

    uid_user = models.execute_kw(db, uid, pwd, "res.users", "search",
        [[]], {"limit": 1})[0]

    today = date.today().isoformat()
    in_5_days = (date.today() + timedelta(days=5)).isoformat()
    in_10_days = (date.today() + timedelta(days=10)).isoformat()

    # 3 requests per company in different states
    requests_spec = [
        {"state_after": "draft",       "note": "Monthly consumables restock",       "days_req": in_5_days},
        {"state_after": "submitted",   "note": "Urgent medications request",         "days_req": in_5_days},
        {"state_after": "approved",    "note": "Equipment maintenance supplies",     "days_req": in_10_days},
    ]

    created = 0
    for comp in companies:
        for i, spec in enumerate(requests_spec):
            # Pick 3-5 random products for each request
            prods = random.sample(product_ids, min(4, len(product_ids)))
            lines = [
                {
                    "product_id": pid,
                    "quantity_requested": random.choice([5, 10, 20, 50, 100]),
                    "quantity_approved": random.choice([5, 10, 20, 50, 100]),
                    "notes": f"Regular stock — batch {i+1}",
                }
                for pid in prods
            ]
            req_vals = {
                "company_id": comp["id"],
                "requested_by": uid_user,
                "request_date": today,
                "required_date": spec["days_req"],
                "notes": f"{spec['note']} — {comp['name']}",
                "line_ids": [[0, 0, line] for line in lines],
            }
            req_id = models.execute_kw(db, uid, pwd,
                "clinic.supply.request", "create", [req_vals])
            print(f"  [CREATE] supply.request id={req_id} [{comp['name']}] state=draft")

            # Advance state
            target = spec["state_after"]
            if target in ("submitted", "approved"):
                models.execute_kw(db, uid, pwd, "clinic.supply.request",
                    "action_submit", [[req_id]])
                print(f"    → submitted")
            if target == "approved":
                models.execute_kw(db, uid, pwd, "clinic.supply.request",
                    "action_approve", [[req_id]])
                print(f"    → approved")
            created += 1

    print(f"  Total: {created} supply requests created")


# ─── MAIN ─────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Load HR, Inventory and Clinic Settings demo data")
    parser.add_argument("--url",      default="http://localhost:8070")
    parser.add_argument("--db",       default="odoo_clinic")
    parser.add_argument("--user",     default="admin@clinic.local")
    parser.add_argument("--password", default="admin_local_strong")
    parser.add_argument("--skip-settings",  action="store_true")
    parser.add_argument("--skip-hr",        action="store_true")
    parser.add_argument("--skip-inventory", action="store_true")
    args = parser.parse_args()

    uid, models = connect(args.url, args.db, args.user, args.password)

    if not args.skip_settings:
        try:
            load_clinic_settings(models, args.db, uid, args.password)
        except Exception as e:
            print(f"  [ERROR] Clinic Settings: {e}")

    if not args.skip_hr:
        try:
            load_hr_shifts(models, args.db, uid, args.password)
        except Exception as e:
            print(f"  [ERROR] HR Shifts: {e}")
        try:
            load_hr_employees(models, args.db, uid, args.password)
        except Exception as e:
            print(f"  [ERROR] HR Employees: {e}")

    product_ids = []
    if not args.skip_inventory:
        try:
            product_ids = load_inventory_products(models, args.db, uid, args.password)
        except Exception as e:
            print(f"  [ERROR] Inventory Products: {e}")
        if product_ids:
            try:
                load_supply_requests(models, args.db, uid, args.password, product_ids)
            except Exception as e:
                print(f"  [ERROR] Supply Requests: {e}")

    print("\n========================================")
    print("  Demo data load complete!")
    print(f"  Open: {args.url}")
    print("========================================")


if __name__ == "__main__":
    main()
