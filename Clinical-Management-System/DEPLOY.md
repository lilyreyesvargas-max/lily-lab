# DEPLOY.md — Guía de Despliegue

## Requisitos

- Docker 20.10+
- Docker Compose v2+
- 4 GB RAM mínimo (8 GB recomendado)
- 20 GB espacio en disco

## Primer Despliegue (Local)

```bash
# 1. Clonar repositorio
git clone <repo-url>
cd Clinical-Management-System

# 2. Configurar entorno
cp .env.example .env
# Editar .env y cambiar contraseñas si es necesario

# 3. Levantar servicios
docker compose up -d

# 4. Inicializar base de datos (espera ~2-3 min)
make init

# 5. Cargar datos demo (opcional)
make load_demo

# 6. Demo EDI completo (opcional)
make edi-demo
```

## Configuración de Módulos en Odoo

1. Abrir http://localhost:8069
2. Ir a **Settings > Apps**
3. Instalar en este orden:
   - `clinic_core` (instala automáticamente `base`, `mail`, `account`)
   - `clinic_patients`
   - `clinic_ehr`
   - `clinic_appointments`
   - `clinic_insurance_billing`
   - `clinic_inventory_internal`
   - `clinic_hr_payroll`
   - `clinic_automation`
   - `clinic_edi_us`

O instalar `clinic_edi_us` directamente — instalará todas las dependencias automáticamente.

## Variables de Entorno

| Variable | Descripción | Default |
|----------|-------------|---------|
| `POSTGRES_DB` | Nombre de la base de datos | `odoo_clinic` |
| `POSTGRES_USER` | Usuario PostgreSQL | `odoo` |
| `POSTGRES_PASSWORD` | Contraseña PostgreSQL | `odoo_pwd_strong` |
| `ODOO_ADMIN_PASS` | Contraseña admin Odoo | `admin_local_strong` |
| `TZ` | Zona horaria | `America/New_York` |
| `EDI_BASE_URL` | URL del mock clearinghouse | `http://mock-clearinghouse:18080` |
| `EDI_SFTP_HOST` | Host SFTP sandbox | `sftp-sandbox` |
| `EDI_SFTP_USER` | Usuario SFTP | `ediuser` |
| `EDI_SFTP_PASS` | Contraseña SFTP | `edipass` |

## Backup y Restore

```bash
# Backup
make backup
# → backups/odoo_clinic_YYYYMMDD_HHMMSS.sql.gz

# Restore
make restore FILE=backups/odoo_clinic_20240101_120000.sql.gz
```

## Actualización de Módulos

```bash
docker compose exec odoo odoo --config=/etc/odoo/odoo.conf \
  --database=odoo_clinic \
  --update=clinic_core,clinic_patients \
  --stop-after-init
```

## Logs

```bash
make logs                          # Follow Odoo logs
docker compose logs mock-clearinghouse  # Mock clearinghouse logs
docker compose logs sftp-sandbox        # SFTP sandbox logs
```

## Puertos

| Servicio | Puerto |
|----------|--------|
| Odoo | 8069 |
| Odoo Longpolling | 8072 |
| Mock Clearinghouse | 18080 |
| SFTP Sandbox | 2222 |
| pgAdmin | 5050 |
| PostgreSQL | 5432 |
