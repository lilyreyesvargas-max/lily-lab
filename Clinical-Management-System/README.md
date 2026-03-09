# Clinical Management System — Odoo 17

Sistema de gestión clínica completo para clínica matriz + 3 sucursales, con módulos Odoo 17 Community, EDI US (837/835/270/271), mock clearinghouse y sandbox SFTP.

## Inicio Rápido

```bash
cp .env.example .env
docker compose up -d
make init
make load_demo
make edi-demo
# Abrir: http://localhost:8069
```

## Módulos Incluidos

| Módulo | Descripción |
|--------|-------------|
| `clinic_core` | Fundación: multi-empresa, ICD-10, grupos de seguridad |
| `clinic_patients` | Pacientes, aseguradoras, pólizas, consentimientos |
| `clinic_ehr` | Historia Clínica Electrónica: encounters, diagnósticos, notas por especialidad |
| `clinic_appointments` | Agenda de citas por sede: calendario, kanban, estados |
| `clinic_insurance_billing` | Cobertura, split billing (paciente + aseguradora), claims, ERA 835 |
| `clinic_inventory_internal` | Insumos: solicitud → aprobación → transferencia → consumo |
| `clinic_hr_payroll` | RRHH: empleados por sede, roles clínicos, turnos |
| `clinic_automation` | Recordatorios de citas, alertas de stock, jobs EDI (cron) |
| `clinic_edi_us` | EDI US: 837P/835/270/271, validación X12, conectores REST/SFTP |

## Arquitectura

```
HQ Clinic ─┬─ Branch S1
           ├─ Branch S2
           └─ Branch S3
```

- **Odoo 17 Community** en contenedor Docker
- **PostgreSQL 14** con volumen persistente
- **Mock Clearinghouse** FastAPI en puerto 18080
- **SFTP Sandbox** (atmoz/sftp) en puerto 2222
- **pgAdmin** en puerto 5050

## Servicios Docker

```
odoo              → http://localhost:8069
mock-clearinghouse → http://localhost:18080
sftp-sandbox       → sftp://localhost:2222
pgadmin            → http://localhost:5050
```

## Flujo EDI

```
Odoo Encounters
      ↓
make edi-generate-837  → edi/out/837/*.txt
      ↓
make edi-send-837      → POST /submit-837 → mock clearinghouse
      ↓
make edi-import-835    → importa edi/in/835/*.txt → Odoo Remittances
      ↓
make edi-request-eligibility → POST /eligibility/270
      ↓
make edi-process-eligibility → procesa 271 → actualiza Odoo
```

## Seguridad

Grupos disponibles:
- `Clinic / Administrator` — acceso total
- `Clinic / Doctor` — CRUD en EHR y citas
- `Clinic / Nurse` — lectura/escritura en EHR e insumos
- `Clinic / Receptionist` — gestión de pacientes y citas
- `Clinic / Billing` — claims y remesas

## Comandos Make

```bash
make help              # Lista todos los comandos
make init              # Setup inicial (primera vez)
make up/down           # Iniciar/detener servicios
make logs              # Ver logs de Odoo
make backup            # Backup de la base de datos
make test              # Ejecutar tests de módulos
make load_demo         # Cargar datos demo
make edi-demo          # Demo completo del flujo EDI
```

## Especialidades Configuradas

1. Medicina General
2. Ginecología
3. Oftalmología
4. Estomatología

(Extensible vía `clinic.specialty`)
