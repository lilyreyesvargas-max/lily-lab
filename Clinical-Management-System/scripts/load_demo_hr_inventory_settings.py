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
    common = xmlrpc.client.ServerProxy(f"{url}/xmlrpc/2/common", allow_none=True)
    uid = common.authenticate(db, user, password, {})
    if not uid:
        print(f"[ERROR] Authentication failed for {user}")
        sys.exit(1)
    models = xmlrpc.client.ServerProxy(f"{url}/xmlrpc/2/object", allow_none=True)
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

# Specialties catalog (name, code, description, color)
SPECIALTIES_CATALOG = [
    ("Medicina General",   "MG",  "Primary care and general medicine",             1),
    ("Ginecología",        "GIN", "Women's health and reproductive medicine",       2),
    ("Oftalmología",       "OFT", "Eye care and vision health",                     3),
    ("Estomatología",      "EST", "Oral health and dental medicine",                4),
    ("Pediatría",          "PED", "Medical care for infants and children",          5),
    ("Cardiología",        "CAR", "Heart and cardiovascular system",                6),
    ("Dermatología",       "DER", "Skin, hair and nail conditions",                 7),
    ("Ortopedia",          "ORT", "Bone, joint and musculoskeletal disorders",      8),
]

# Contact data per branch
BRANCH_CONTACT = {
    "Main Clinic HQ": {
        "phone": "+1 (212) 555-0100", "email": "hq@clinic.local",
        "street": "350 Fifth Avenue", "city": "New York", "zip": "10118",
        "website": "http://clinic.local",
    },
    "Clinic Branch S1": {
        "phone": "+1 (718) 555-0101", "email": "s1@clinic.local",
        "street": "1234 Atlantic Ave", "city": "Brooklyn", "zip": "11216",
        "website": "http://s1.clinic.local",
    },
    "Clinic Branch S2": {
        "phone": "+1 (718) 555-0202", "email": "s2@clinic.local",
        "street": "89-11 Jamaica Ave", "city": "Queens", "zip": "11421",
        "website": "http://s2.clinic.local",
    },
    "Clinic Branch S3": {
        "phone": "+1 (718) 555-0303", "email": "s3@clinic.local",
        "street": "2432 Grand Concourse", "city": "Bronx", "zip": "10458",
        "website": "http://s3.clinic.local",
    },
}


def load_clinic_settings(models, db, uid, pwd):
    print("\n=== CLINIC SETTINGS — SPECIALTIES ===")

    # ── 1a. Ensure full specialty catalog ────────────────────────────────────
    spec_map = {}
    for name, code, desc, color in SPECIALTIES_CATALOG:
        existing = models.execute_kw(db, uid, pwd, "clinic.specialty", "search",
            [[("code", "=", code)]], {"limit": 1})
        if existing:
            spec_map[name] = existing[0]
            print(f"  [OK]     specialty '{name}' → id={existing[0]}")
        else:
            sid = models.execute_kw(db, uid, pwd, "clinic.specialty", "create", [{
                "name": name, "code": code, "description": desc, "color": color,
            }])
            spec_map[name] = sid
            print(f"  [CREATE] specialty '{name}' → id={sid}")

    # ── 1b. Get companies ─────────────────────────────────────────────────────
    print("\n=== CLINIC SETTINGS — COMPANY CONFIG ===")
    companies = models.execute_kw(db, uid, pwd, "res.company", "search_read",
        [[("name", "in", list(BRANCH_CONTACT.keys()))]],
        {"fields": ["id", "name"]})

    if not companies:
        print("  [WARN] Companies not found — run load_demo_data.py first")
        return

    # Specialty assignment per branch
    branch_specs = {
        "Main Clinic HQ":   [spec_map[s] for s in [
            "Medicina General","Ginecología","Oftalmología","Estomatología",
            "Pediatría","Cardiología","Dermatología","Ortopedia"]],
        "Clinic Branch S1": [spec_map[s] for s in [
            "Medicina General","Ginecología","Pediatría"]],
        "Clinic Branch S2": [spec_map[s] for s in [
            "Oftalmología","Estomatología","Dermatología"]],
        "Clinic Branch S3": [spec_map[s] for s in [
            "Medicina General","Estomatología","Ortopedia"]],
    }
    codes = {
        "Main Clinic HQ": "HQ-001", "Clinic Branch S1": "S1-001",
        "Clinic Branch S2": "S2-001", "Clinic Branch S3": "S3-001",
    }
    notes = {
        "Main Clinic HQ":   "Main headquarters — all 8 specialties. Administrative center.",
        "Clinic Branch S1": "Brooklyn branch — Medicina General, Ginecología, Pediatría.",
        "Clinic Branch S2": "Queens branch — Oftalmología, Estomatología, Dermatología.",
        "Clinic Branch S3": "Bronx branch — Medicina General, Estomatología, Ortopedia.",
    }

    for comp in companies:
        cname = comp["name"]
        contact = BRANCH_CONTACT.get(cname, {})

        # Update company contact info
        models.execute_kw(db, uid, pwd, "res.company", "write", [[comp["id"]], contact])
        print(f"  [UPDATE] company contact '{cname}'")

        # Update or create clinic.config
        sids = branch_specs.get(cname, [])
        config_vals = {
            "company_id": comp["id"],
            "clinic_code": codes.get(cname, "CLI-000"),
            "specialty_ids": [[6, 0, sids]],
            "notes": notes.get(cname, ""),
        }
        existing = models.execute_kw(db, uid, pwd, "clinic.config", "search",
            [[("company_id", "=", comp["id"])]], {"limit": 1})
        if existing:
            models.execute_kw(db, uid, pwd, "clinic.config", "write", [existing, config_vals])
            print(f"  [UPDATE] clinic.config '{cname}' — {len(sids)} specialties")
        else:
            new_id = models.execute_kw(db, uid, pwd, "clinic.config", "create", [config_vals])
            print(f"  [CREATE] clinic.config '{cname}' → id={new_id}")

    # ── 1c. Automation config per company ─────────────────────────────────────
    print("\n=== CLINIC SETTINGS — AUTOMATION CONFIG ===")
    auto_settings = {
        "Main Clinic HQ":   {"reminder_hours_before": 24, "expiry_days_warning": 30,
                             "edi_auto_send": True,  "edi_auto_import": True},
        "Clinic Branch S1": {"reminder_hours_before": 24, "expiry_days_warning": 30,
                             "edi_auto_send": False, "edi_auto_import": False},
        "Clinic Branch S2": {"reminder_hours_before": 12, "expiry_days_warning": 14,
                             "edi_auto_send": False, "edi_auto_import": False},
        "Clinic Branch S3": {"reminder_hours_before": 48, "expiry_days_warning": 45,
                             "edi_auto_send": False, "edi_auto_import": False},
    }
    for comp in companies:
        cname = comp["name"]
        asettings = auto_settings.get(cname, {})
        auto_vals = {
            "company_id": comp["id"],
            "reminder_enabled": True,
            "reminder_hours_before": asettings.get("reminder_hours_before", 24),
            "stock_alert_enabled": True,
            "stock_min_qty": 5.0,
            "expiry_alert_enabled": True,
            "expiry_days_warning": asettings.get("expiry_days_warning", 30),
            "edi_auto_send": asettings.get("edi_auto_send", False),
            "edi_auto_import": asettings.get("edi_auto_import", False),
        }
        existing_auto = models.execute_kw(db, uid, pwd, "clinic.automation.config", "search",
            [[("company_id", "=", comp["id"])]], {"limit": 1})
        if existing_auto:
            models.execute_kw(db, uid, pwd, "clinic.automation.config", "write",
                [existing_auto, auto_vals])
            print(f"  [UPDATE] automation.config '{cname}'")
        else:
            new_id = models.execute_kw(db, uid, pwd, "clinic.automation.config", "create",
                [auto_vals])
            print(f"  [CREATE] automation.config '{cname}' → id={new_id}")


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

def _create_or_get_user(models, db, uid, pwd, name, login, password="Clinic2026!"):
    """Create a res.users record if it does not exist, return user id."""
    existing = models.execute_kw(db, uid, pwd, "res.users", "search",
        [[("login", "=", login)]], {"limit": 1})
    if existing:
        return existing[0]
    group_user_id = get_ref(models, db, uid, pwd, "base.group_user")
    user_vals = {
        "name": name,
        "login": login,
        "email": login,
        "password": password,
    }
    if group_user_id:
        user_vals["groups_id"] = [[4, group_user_id]]
    return models.execute_kw(db, uid, pwd, "res.users", "create", [user_vals])


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

    # Get or create hr.job positions required by clinic staff
    job_positions = {}
    for job_name in [
        "General Physician", "Gynecologist", "Ophthalmologist", "Dentist",
        "Registered Nurse", "Receptionist", "HR Administrator", "Billing Administrator",
    ]:
        jid = search_or_create(models, db, uid, pwd,
            "hr.job",
            [("name", "=", job_name)],
            {"name": job_name})
        job_positions[job_name] = jid
    print(f"  [OK] {len(job_positions)} hr.job positions ready")

    employees = [
        # Doctors — Main HQ
        {"name": "Dr. Carlos Mendoza",   "role": "doctor",       "specialty": "Medicina General",
         "license": "MD-001-NY", "company": "Main Clinic HQ",    "dept": "Medical",
         "job": "General Physician",     "email": "c.mendoza@clinic.local",    "badge": "BADGE-0001"},
        {"name": "Dr. Ana Gutiérrez",    "role": "doctor",       "specialty": "Ginecología",
         "license": "MD-002-NY", "company": "Main Clinic HQ",    "dept": "Medical",
         "job": "Gynecologist",          "email": "a.gutierrez@clinic.local",  "badge": "BADGE-0002"},
        {"name": "Dr. Roberto Silva",    "role": "doctor",       "specialty": "Oftalmología",
         "license": "MD-003-NY", "company": "Main Clinic HQ",    "dept": "Medical",
         "job": "Ophthalmologist",       "email": "r.silva@clinic.local",      "badge": "BADGE-0003"},
        {"name": "Dr. María Torres",     "role": "doctor",       "specialty": "Estomatología",
         "license": "MD-004-NY", "company": "Main Clinic HQ",    "dept": "Medical",
         "job": "Dentist",               "email": "m.torres@clinic.local",     "badge": "BADGE-0004"},
        # Doctors — Branch S1
        {"name": "Dr. Luis Ramírez",     "role": "doctor",       "specialty": "Medicina General",
         "license": "MD-005-NY", "company": "Clinic Branch S1",  "dept": "Medical",
         "job": "General Physician",     "email": "l.ramirez@clinic.local",    "badge": "BADGE-0005"},
        {"name": "Dr. Sofía Castro",     "role": "doctor",       "specialty": "Ginecología",
         "license": "MD-006-NY", "company": "Clinic Branch S1",  "dept": "Medical",
         "job": "Gynecologist",          "email": "s.castro@clinic.local",     "badge": "BADGE-0006"},
        # Doctors — Branch S2
        {"name": "Dr. Juan Morales",     "role": "doctor",       "specialty": "Oftalmología",
         "license": "MD-007-NY", "company": "Clinic Branch S2",  "dept": "Medical",
         "job": "Ophthalmologist",       "email": "j.morales@clinic.local",    "badge": "BADGE-0007"},
        {"name": "Dr. Elena Vega",       "role": "doctor",       "specialty": "Estomatología",
         "license": "MD-008-NY", "company": "Clinic Branch S2",  "dept": "Medical",
         "job": "Dentist",               "email": "e.vega@clinic.local",       "badge": "BADGE-0008"},
        # Doctors — Branch S3
        {"name": "Dr. Pedro Flores",     "role": "doctor",       "specialty": "Medicina General",
         "license": "MD-009-NY", "company": "Clinic Branch S3",  "dept": "Medical",
         "job": "General Physician",     "email": "p.flores@clinic.local",     "badge": "BADGE-0009"},
        {"name": "Dr. Carmen Ruiz",      "role": "doctor",       "specialty": "Estomatología",
         "license": "MD-010-NY", "company": "Clinic Branch S3",  "dept": "Medical",
         "job": "Dentist",               "email": "c.ruiz@clinic.local",       "badge": "BADGE-0010"},
        # Nurses
        {"name": "Enf. Patricia López",  "role": "nurse",        "specialty": "Medicina General",
         "license": "RN-001-NY", "company": "Main Clinic HQ",    "dept": "Nursing",
         "job": "Registered Nurse",      "email": "p.lopez@clinic.local",      "badge": "BADGE-0011"},
        {"name": "Enf. Diego Hernández", "role": "nurse",        "specialty": "Ginecología",
         "license": "RN-002-NY", "company": "Main Clinic HQ",    "dept": "Nursing",
         "job": "Registered Nurse",      "email": "d.hernandez@clinic.local",  "badge": "BADGE-0012"},
        {"name": "Enf. Isabel Jiménez",  "role": "nurse",        "specialty": "Medicina General",
         "license": "RN-003-NY", "company": "Clinic Branch S1",  "dept": "Nursing",
         "job": "Registered Nurse",      "email": "i.jimenez@clinic.local",    "badge": "BADGE-0013"},
        {"name": "Enf. Miguel Ángel García", "role": "nurse",    "specialty": "Oftalmología",
         "license": "RN-004-NY", "company": "Clinic Branch S2",  "dept": "Nursing",
         "job": "Registered Nurse",      "email": "ma.garcia@clinic.local",    "badge": "BADGE-0014"},
        # Receptionists
        {"name": "Rec. Laura Martínez",  "role": "receptionist", "specialty": None,
         "license": None,        "company": "Main Clinic HQ",    "dept": "Reception",
         "job": "Receptionist",          "email": "la.martinez@clinic.local",  "badge": "BADGE-0015"},
        {"name": "Rec. Andrés Sánchez",  "role": "receptionist", "specialty": None,
         "license": None,        "company": "Clinic Branch S1",  "dept": "Reception",
         "job": "Receptionist",          "email": "an.sanchez@clinic.local",   "badge": "BADGE-0016"},
        {"name": "Rec. Verónica Díaz",   "role": "receptionist", "specialty": None,
         "license": None,        "company": "Clinic Branch S2",  "dept": "Reception",
         "job": "Receptionist",          "email": "ve.diaz@clinic.local",      "badge": "BADGE-0017"},
        {"name": "Rec. Fernando Reyes",  "role": "receptionist", "specialty": None,
         "license": None,        "company": "Clinic Branch S3",  "dept": "Reception",
         "job": "Receptionist",          "email": "fe.reyes@clinic.local",     "badge": "BADGE-0018"},
        # Admin
        {"name": "Adm. Gloria Pérez",    "role": "admin",        "specialty": None,
         "license": None,        "company": "Main Clinic HQ",    "dept": "Administration",
         "job": "HR Administrator",      "email": "gl.perez@clinic.local",     "badge": "BADGE-0019"},
        {"name": "Adm. Ricardo Vargas",  "role": "admin",        "specialty": None,
         "license": None,        "company": "Main Clinic HQ",    "dept": "Administration",
         "job": "Billing Administrator", "email": "ri.vargas@clinic.local",    "badge": "BADGE-0020"},
    ]

    expiry = (date.today() + timedelta(days=730)).isoformat()  # 2 years

    created = 0
    for emp in employees:
        company_id = comp_map.get(emp["company"])
        if not company_id:
            # fallback to any company
            company_id = models.execute_kw(db, uid, pwd, "res.company", "search",
                [[]], {"limit": 1})[0]

        # Create (or retrieve) the linked Odoo user for this employee
        emp_user_id = _create_or_get_user(
            models, db, uid, pwd,
            name=emp["name"],
            login=emp["email"],
        )

        vals = {
            "name": emp["name"],
            "job_title": emp["job"],
            "work_email": emp["email"],
            "clinic_role": emp["role"],
            "company_id": company_id,
            "department_id": departments.get(emp["dept"]),
            "user_id": emp_user_id,
            "barcode": emp["badge"],
            "job_id": job_positions.get(emp["job"]),
        }
        if emp["specialty"] and emp["specialty"] in spec_map:
            vals["specialty_id"] = spec_map[emp["specialty"]]
        if emp["license"]:
            vals["license_number"] = emp["license"]
            vals["license_expiry"] = expiry

        # search_or_create: if employee already exists, update with missing fields
        existing_ids = models.execute_kw(db, uid, pwd, "hr.employee", "search",
            [[("name", "=", emp["name"])]], {"limit": 1})
        if existing_ids:
            eid = existing_ids[0]
            models.execute_kw(db, uid, pwd, "hr.employee", "write",
                [[eid], vals])
            print(f"  [UPDATE] {emp['role']:15s} {emp['name']} → id={eid}")
        else:
            eid = models.execute_kw(db, uid, pwd, "hr.employee", "create", [vals])
            print(f"  [CREATE] {emp['role']:15s} {emp['name']} → id={eid}")
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

    # Get UoM — use existing "Units" (or any available)
    uom_candidates = models.execute_kw(db, uid, pwd, "uom.uom", "search_read",
        [[("name", "in", ["Units", "Unit", "Unidades", "ud"])]], {"fields": ["id", "name"], "limit": 1})
    uom_unit = uom_candidates[0]["id"] if uom_candidates else None
    if not uom_unit:
        uom_fallback = models.execute_kw(db, uid, pwd, "uom.uom", "search", [[]], {"limit": 1})
        uom_unit = uom_fallback[0] if uom_fallback else None

    products = [
        # Consumables
        # Odoo 17: detailed_type values are 'consu' (consumable) and 'product' (storable)
        {"name": "Surgical Gloves (Box 100)",   "ref": "INS-001", "cat": cat_consumable, "type": "consu",   "price": 12.50},
        {"name": "Face Masks (Box 50)",          "ref": "INS-002", "cat": cat_consumable, "type": "consu",   "price": 8.00},
        {"name": "Sterile Gauze 4x4",           "ref": "INS-003", "cat": cat_consumable, "type": "consu",   "price": 5.75},
        {"name": "Adhesive Bandages (Box 100)",  "ref": "INS-004", "cat": cat_consumable, "type": "consu",   "price": 6.50},
        {"name": "Syringes 5ml (Box 100)",       "ref": "INS-005", "cat": cat_consumable, "type": "consu",   "price": 15.00},
        {"name": "Syringes 10ml (Box 50)",       "ref": "INS-006", "cat": cat_consumable, "type": "consu",   "price": 14.00},
        {"name": "Alcohol Swabs (Box 200)",      "ref": "INS-007", "cat": cat_consumable, "type": "consu",   "price": 4.25},
        {"name": "Cotton Rolls 500g",            "ref": "INS-008", "cat": cat_consumable, "type": "consu",   "price": 3.50},
        {"name": "Tongue Depressors (Box 100)",  "ref": "INS-009", "cat": cat_consumable, "type": "consu",   "price": 4.00},
        {"name": "Disposable Drapes",            "ref": "INS-010", "cat": cat_consumable, "type": "consu",   "price": 18.00},
        # Medications
        {"name": "Ibuprofen 400mg (Box 30)",     "ref": "MED-001", "cat": cat_medication, "type": "consu",   "price": 9.00},
        {"name": "Amoxicillin 500mg (Box 21)",   "ref": "MED-002", "cat": cat_medication, "type": "consu",   "price": 12.00},
        {"name": "Paracetamol 500mg (Box 24)",   "ref": "MED-003", "cat": cat_medication, "type": "consu",   "price": 5.00},
        {"name": "Lidocaine 2% 50ml",            "ref": "MED-004", "cat": cat_medication, "type": "consu",   "price": 22.00},
        {"name": "Saline Solution 500ml",        "ref": "MED-005", "cat": cat_medication, "type": "consu",   "price": 7.50},
        {"name": "Hydrogen Peroxide 1L",         "ref": "MED-006", "cat": cat_medication, "type": "consu",   "price": 6.00},
        {"name": "Eye Drops (Artificial Tears)", "ref": "MED-007", "cat": cat_medication, "type": "consu",   "price": 14.50},
        {"name": "Antifungal Cream 30g",         "ref": "MED-008", "cat": cat_medication, "type": "consu",   "price": 11.00},
        # Equipment (storable)
        {"name": "Disposable Thermometer",       "ref": "EQP-001", "cat": cat_equipment,  "type": "product", "price": 35.00},
        {"name": "Blood Pressure Cuff",          "ref": "EQP-002", "cat": cat_equipment,  "type": "product", "price": 85.00},
        {"name": "Pulse Oximeter",               "ref": "EQP-003", "cat": cat_equipment,  "type": "product", "price": 45.00},
        {"name": "Otoscope Disposable Tip",      "ref": "EQP-004", "cat": cat_equipment,  "type": "consu",   "price": 8.00},
        {"name": "Dental Probe Set",             "ref": "EQP-005", "cat": cat_equipment,  "type": "product", "price": 120.00},
        {"name": "Ophthalmoscope Lens",          "ref": "EQP-006", "cat": cat_equipment,  "type": "product", "price": 95.00},
    ]

    product_ids = []
    for p in products:
        vals = {
            "name": p["name"],
            "default_code": p["ref"],
            "categ_id": p["cat"],
            "detailed_type": p["type"],
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

            # Advance state via write (action_* methods return None → XML-RPC error)
            target = spec["state_after"]
            if target in ("submitted", "approved"):
                models.execute_kw(db, uid, pwd, "clinic.supply.request", "write",
                    [[req_id], {"state": "submitted"}])
                print(f"    → submitted")
            if target == "approved":
                models.execute_kw(db, uid, pwd, "clinic.supply.request", "write",
                    [[req_id], {"state": "approved", "approved_by": uid_user}])
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
