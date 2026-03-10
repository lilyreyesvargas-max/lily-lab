# Clinical Management System

> **Enterprise-grade clinical operations platform** built on Odoo 17 Community, designed for multi-site healthcare organizations with full US EDI compliance (X12 837/835/270/271).

![Odoo](https://img.shields.io/badge/Odoo-17.0%20Community-714B67?logo=odoo)
![Python](https://img.shields.io/badge/Python-3.10+-3776AB?logo=python&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-14+-336791?logo=postgresql&logoColor=white)
![License](https://img.shields.io/badge/License-LGPL--3-blue)

---

## Overview

The **Clinical Management System (CMS)** is a fully integrated, containerized healthcare ERP built on the Odoo 17 platform. It provides end-to-end clinical operations management for a headquarters clinic and up to N branch locations, covering patient management, electronic health records, scheduling, insurance billing, EDI transactions, internal inventory, and HR/payroll — all from a single platform.

### Key Capabilities

| Domain | Capability |
|--------|-----------|
| **Patient Management** | Registration, insurance policies, consent management |
| **Clinical (EHR)** | Encounters, diagnoses (ICD-10), specialty-specific notes |
| **Scheduling** | Multi-site appointment calendar with kanban view |
| **Billing & Insurance** | Split billing, claim lifecycle, ERA 835 reconciliation |
| **EDI Compliance** | X12 837P/835/270/271 with sandbox clearinghouse |
| **Inventory** | Internal supply request → approval → transfer → consumption |
| **HR & Payroll** | Clinical roles, shift management, multi-site employees |
| **Automation** | Appointment reminders, stock alerts, scheduled EDI jobs |

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    Clinical Management System                │
│                                                              │
│   ┌─────────────────────────────────────────────────────┐   │
│   │              Odoo 17 Community (Port 8070)           │   │
│   │                                                     │   │
│   │  clinic_core │ clinic_patients │ clinic_ehr         │   │
│   │  clinic_appointments │ clinic_insurance_billing      │   │
│   │  clinic_inventory_internal │ clinic_hr_payroll       │   │
│   │  clinic_edi_us │ clinic_automation                   │   │
│   └─────────────────────────────────────────────────────┘   │
│           │                    │                             │
│   ┌───────▼──────┐    ┌────────▼──────────────────────┐     │
│   │ PostgreSQL 14 │    │   Mock Clearinghouse (18080)  │     │
│   │   (Port 5433) │    │   FastAPI · X12 EDI Sandbox   │     │
│   └───────────────┘    └───────────────────────────────┘     │
│                                                              │
│   Multi-Company Structure:                                   │
│   HQ Clinic ─┬─ Branch S1 (Brooklyn)                        │
│              ├─ Branch S2 (Queens)                           │
│              └─ Branch S3 (Bronx)                            │
└──────────────────────────────────────────────────────────────┘
```

### Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| ERP Platform | Odoo Community | 17.0 |
| Backend Language | Python | 3.10+ |
| Database | PostgreSQL | 14+ |
| Containerization | Docker Compose | v2 |
| EDI Mock Server | FastAPI | Latest |
| SFTP Sandbox | atmoz/sftp | Latest |
| Frontend | OWL (Odoo Web Library) | 17.x |

---

## Module Reference

### Core Modules

| Module | Technical Name | Description |
|--------|---------------|-------------|
| **Clinic Core** | `clinic_core` | Foundation layer: multi-company setup (HQ + branches), ICD-10 code catalog, security groups, clinic parameters, OWL dashboard |
| **Patients** | `clinic_patients` | Patient registry, insurers, insurance policies, consent records |
| **EHR** | `clinic_ehr` | Electronic Health Records: encounters, clinical attachments, ICD-10 diagnoses, specialty-specific sub-modules |
| **Appointments** | `clinic_appointments` | Multi-site scheduling with calendar and kanban views, physician-per-site configuration |
| **Insurance & Billing** | `clinic_insurance_billing` | Coverage plans, split billing engine (patient + insurer), claim lifecycle, ERA 835 remittance matching |
| **Inventory** | `clinic_inventory_internal` | Internal supply management: request → approval → stock transfer → consumption, lot/expiry tracking |
| **HR & Payroll** | `clinic_hr_payroll` | Clinical roles (doctor/nurse/receptionist), shift management, per-site employee assignment |
| **EDI US** | `clinic_edi_us` | X12 EDI: 837P claim generation, 835 ERA import, 270/271 eligibility, REST/SFTP connectors, transaction log |
| **Automation** | `clinic_automation` | Scheduled jobs: appointment reminders, low-stock/expiry alerts, automated EDI dispatch |

### Clinical Specialties (Pre-configured)

| Code | Specialty |
|------|-----------|
| MG | Medicina General |
| GIN | Ginecología |
| OFT | Oftalmología |
| EST | Estomatología |

> Specialties are fully extensible via `Clinic → Configuration → Specialties`.

---

## Security Model

Role-based access control with 5 clinical profiles:

| Group | Technical ID | Permissions |
|-------|-------------|-------------|
| **Administrator** | `clinic_group_admin` | Full access — all modules, configuration, reports |
| **Doctor** | `clinic_group_doctor` | CRUD on EHR, appointments; read on billing |
| **Nurse** | `clinic_group_nurse` | Read/write on EHR and supply requests |
| **Receptionist** | `clinic_group_receptionist` | Patient management, appointment scheduling |
| **Billing** | `clinic_group_billing` | Claims, remittances, EDI transactions |

> All roles inherit from `base.group_user` (Odoo internal user).

---

## EDI Workflow

The system implements the full US healthcare EDI cycle in a local sandbox environment:

```
┌─────────────────────────────────────────────────────────────┐
│                       EDI Cycle                             │
│                                                             │
│  1. Clinical Encounters (Odoo EHR)                          │
│           │                                                 │
│           ▼                                                 │
│  2. make edi-generate-837                                   │
│     → Generates X12 837P claim files → edi/out/837/        │
│           │                                                 │
│           ▼                                                 │
│  3. make edi-send-837                                       │
│     → POST multipart/form-data → Mock Clearinghouse :18080  │
│     → Creates clinic.edi.transaction (outbound)             │
│           │                                                 │
│           ▼                                                 │
│  4. make edi-import-835                                     │
│     → Parses X12 835 ERA → Creates clinic.remittance        │
│     → Matches insurer, links payment → Creates EDI log      │
│           │                                                 │
│           ▼                                                 │
│  5. make edi-request-eligibility                            │
│     → POST 270 request → Mock Clearinghouse                 │
│           │                                                 │
│           ▼                                                 │
│  6. make edi-process-eligibility                            │
│     → Parses 271 response → Updates patient eligibility     │
└─────────────────────────────────────────────────────────────┘
```

### EDI File Structure

```
edi/
├── in/
│   ├── 835/          # ERA remittance files (inbound)
│   ├── 271/          # Eligibility responses (inbound)
│   └── processed/    # Archived after import
├── out/
│   ├── 837/          # Generated claim files (outbound)
│   ├── 270/          # Eligibility requests (outbound)
│   └── logs/         # Clearinghouse response logs
└── samples/          # Reference X12 files for testing
```

---

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Docker Engine | 24.0+ | With Compose v2 plugin |
| Python | 3.10+ | For local scripts only |
| GNU Make | 4.0+ | For `make` commands |
| Available ports | 8070, 5433, 18080, 2222, 5050 | See port conflict notes |

> **Port Note:** The system uses non-default ports to avoid conflicts with other local services (PostgreSQL on 5433, Odoo on 8070).

---

## Deployment

### 1. Clone and Configure

```bash
git clone <repository-url>
cd Clinical-Management-System
cp .env.example .env
# Edit .env if you need to adjust ports or credentials
```

### 2. Launch Infrastructure

```bash
sudo docker compose up -d
# Wait ~30 seconds for services to initialize
```

### 3. Initialize Database and Install Modules

```bash
make init
# This will:
#   - Wait for PostgreSQL to be ready
#   - Create odoo_clinic database
#   - Install all 9 clinic modules in dependency order
#   - Assign Clinic/Administrator role to admin user
```

### 4. Load Demo Data

```bash
make load_demo                # Core data: patients, insurers, base HR
make load_demo_hr_inventory   # HR details, inventory, supply requests, clinic settings
make edi-demo                 # Full EDI sandbox cycle
```

### 5. Access the System

| Service | URL | Credentials |
|---------|-----|-------------|
| **Odoo CMS** | http://localhost:8070 | `admin@clinic.local` / `admin_local_strong` |
| **Mock Clearinghouse API** | http://localhost:18080/docs | — |
| **pgAdmin** | http://localhost:5050 | `admin@pgadmin.local` / `pgadmin` |
| **SFTP Sandbox** | `sftp://localhost:2222` | `ediuser` / `edipass` |

---

## Make Commands Reference

### Infrastructure

```bash
make init              # First-time setup: DB creation + module installation
make up                # Start all Docker services
make down              # Stop all Docker services
make logs              # Follow Odoo container logs
make shell-odoo        # Open Bash shell inside Odoo container
make shell-db          # Open Bash shell inside DB container
make psql              # psql session on odoo_clinic database
make backup            # Backup database to ./backups/ (gzip)
make restore FILE=...  # Restore from backup file
```

### Demo Data

```bash
make load_demo                # Patients, insurers, base employees (40 patients, 6 insurers)
make load_demo_hr_inventory   # HR, inventory, supply requests, clinic settings
```

### EDI

```bash
make edi-generate-837          # Generate 837P claim files from Odoo encounters
make edi-send-837              # Submit claims to mock clearinghouse
make edi-import-835            # Import ERA 835 remittances into Odoo
make edi-request-eligibility   # Send 270 eligibility request
make edi-process-eligibility   # Process 271 eligibility response into Odoo
make edi-demo                  # Run full EDI cycle end-to-end
```

### Quality

```bash
make test              # Run Odoo module test suite
make help              # List all available commands with descriptions
```

---

## Module Update (Post-Deploy)

To apply code changes to an already-running Odoo instance:

```bash
# Stop Odoo, run update in isolated container, restart
sudo docker compose stop odoo
sudo docker compose run --rm odoo odoo \
  --config=/etc/odoo/odoo.conf \
  --database=odoo_clinic \
  --update=clinic_core,clinic_patients,clinic_insurance_billing \
  --stop-after-init
sudo docker compose start odoo
```

---

## Services Overview

| Service | Container | Internal Port | External Port | Purpose |
|---------|-----------|--------------|---------------|---------|
| Odoo 17 | `clinic_odoo` | 8069 | **8070** | Main ERP application |
| PostgreSQL 14 | `clinic_db` | 5432 | **5433** | Database |
| Mock Clearinghouse | `clinic_clearinghouse` | 18080 | 18080 | EDI REST API sandbox |
| SFTP Sandbox | `clinic_sftp` | 22 | 2222 | EDI file transfer sandbox |
| pgAdmin | `clinic_pgadmin` | 80 | 5050 | Database administration UI |

---

## Project Structure

```
Clinical-Management-System/
├── extra-addons/               # Odoo custom modules (9 modules)
│   ├── clinic_core/
│   ├── clinic_patients/
│   ├── clinic_ehr/
│   ├── clinic_appointments/
│   ├── clinic_insurance_billing/
│   ├── clinic_inventory_internal/
│   ├── clinic_hr_payroll/
│   ├── clinic_automation/
│   └── clinic_edi_us/
├── mock_clearinghouse/         # FastAPI EDI mock server
├── scripts/                    # CLI utility scripts
│   ├── load_demo_data.py           # Core demo data loader
│   ├── load_demo_hr_inventory_settings.py  # HR/Inventory demo loader
│   ├── generate_837.py             # 837P claim generator
│   ├── send_837.py                 # Clearinghouse submission
│   ├── import_835.py               # ERA 835 importer
│   ├── eligibility_270.py          # 270 request sender
│   ├── process_271.py              # 271 response processor
│   └── assign_admin_group.py       # Post-install admin setup
├── edi/                        # EDI file directories and samples
├── docker-compose.yml
├── odoo.conf
├── .env.example
├── Makefile
├── DEPLOY.md
└── README.md
```

---

## Configuration

### Environment Variables (`.env`)

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_DB` | `odoo_clinic` | Database name |
| `POSTGRES_USER` | `odoo` | Database user |
| `POSTGRES_PASSWORD` | `odoo_pwd_strong` | Database password |
| `POSTGRES_PORT` | `5433` | External PostgreSQL port |
| `ODOO_ADMIN_PASS` | `admin_local_strong` | Odoo master password |
| `EDI_BASE_URL` | `http://mock-clearinghouse:18080` | Clearinghouse REST endpoint |
| `EDI_SFTP_HOST` | `sftp-sandbox` | SFTP server hostname |
| `EDI_SFTP_USER` | `ediuser` | SFTP username |
| `EDI_SFTP_PASS` | `edipass` | SFTP password |
| `TZ` | `America/New_York` | Timezone |

---

## HIPAA Considerations

This system is designed for **local development and sandbox use**. For production deployment in a HIPAA-regulated environment, additional controls are required:

- Enable TLS/HTTPS on all service endpoints
- Implement encrypted volumes for PHI data at rest
- Configure audit logging (`ir.logging`, `mail.tracking.value`)
- Restrict network access via firewall rules
- Rotate all credentials from `.env.example` defaults
- Review and complete `SECURITY.md` controls checklist

> See `SECURITY.md` for the full HIPAA-ready configuration checklist.

---

## Development

### Adding a New Specialty

1. Navigate to **Clinic → Configuration → Specialties**
2. Create the specialty with name, code, and color
3. Assign to the desired branch via **Clinic → Configuration → Clinic Settings**

### Adding a New Module

Follow the dependency order and add the new module to `extra-addons/`. Declare dependencies in `__manifest__.py` using existing clinic modules as base.

### Running Tests

```bash
make test
# Runs: clinic_core, clinic_patients, clinic_ehr, clinic_appointments,
#        clinic_insurance_billing, clinic_inventory_internal,
#        clinic_hr_payroll, clinic_automation, clinic_edi_us
```

---

## Roadmap

- [ ] Patient portal (self-service appointments)
- [ ] Telemedicine integration (video consultation module)
- [ ] HL7 FHIR API connector
- [ ] Advanced analytics dashboard (OWL + Chart.js)
- [ ] Mobile-responsive EHR forms
- [ ] Multi-language support (ES/EN)
- [ ] Production Kubernetes deployment manifests

---

## License

This project is licensed under the **GNU Lesser General Public License v3.0 (LGPL-3)**, consistent with Odoo Community licensing.

---

## Support

For issues, feature requests, or contributions, please open an issue in the project repository.

> Built with [Odoo Community](https://github.com/odoo/odoo) · Powered by [OWL](https://github.com/odoo/owl) · EDI via X12
