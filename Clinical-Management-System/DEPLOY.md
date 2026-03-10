# DEPLOY.md — Guía de Despliegue

## Requisitos

- Docker 24.0+ con Compose v2 plugin
- 4 GB RAM mínimo (8 GB recomendado para producción)
- 20 GB espacio en disco
- Puertos disponibles: 8070, 8073, 18080, 2222, 5050, 5433

---

## Primer Despliegue (Local / Desarrollo)

```bash
# 1. Clonar repositorio
git clone <repo-url>
cd Clinical-Management-System

# 2. Configurar entorno
cp .env.example .env
# IMPORTANTE: editar .env y cambiar TODAS las contraseñas antes de continuar

# 3. Levantar servicios
sudo docker compose up -d

# 4. Inicializar base de datos + instalar módulos (~3-5 min)
make init

# 5. Cargar datos demo (opcional)
make load_demo
make load_demo_hr_inventory

# 6. Demo EDI completo (opcional)
make edi-demo

# 7. Acceder al sistema
# URL: http://localhost:8070
# Login: admin@clinic.local / admin_local_strong
```

---

## Despliegue en Producción

### Diferencias clave vs desarrollo

| Parámetro | Desarrollo | Producción |
|-----------|-----------|-----------|
| `odoo.conf` | `odoo.conf` (workers=0) | `odoo.prod.conf` (workers=4) |
| `list_db` | True | **False** (oculta selector de BD) |
| `proxy_mode` | False | **True** (detrás de Nginx/Traefik) |
| `log_level` | info | warn |
| `workers` | 0 | **4** (mínimo 2×CPU cores) |
| `max_cron_threads` | 1 | **2** |

### Configurar docker-compose para producción

```bash
# Usar odoo.prod.conf en lugar de odoo.conf
# En docker-compose.yml, cambiar el volumen del config:
# - ./odoo.conf:/etc/odoo/odoo.conf
# + ./odoo.prod.conf:/etc/odoo/odoo.conf

# O sobreescribir con docker-compose.override.yml:
cat > docker-compose.override.yml <<'EOF'
services:
  odoo:
    volumes:
      - ./odoo.prod.conf:/etc/odoo/odoo.conf
EOF
```

### Variables de entorno para producción

Editar `.env` con valores seguros:
```bash
POSTGRES_PASSWORD=<contraseña-fuerte-aleatoria>
ODOO_ADMIN_PASS=<contraseña-fuerte-aleatoria>
EDI_SFTP_PASS=<contraseña-fuerte-aleatoria>
```

> **NUNCA** hacer `git add .env`. El archivo `.gitignore` ya lo excluye.

---

## Seguridad

### Credenciales EDI

Las credenciales del clearinghouse (`rest_api_key`, `sftp_password`) están protegidas:
- Solo visibles para el grupo `Clinic / Administrator`
- Enmascaradas con asteriscos en la UI (`password="True"`)
- **Nunca** se incluyen en exportaciones XML ni en logs

### Record-Level Security (HIPAA)

El sistema implementa `ir.rules` de aislamiento multi-empresa:
- Cada usuario solo ve registros de su(s) sede(s) asignada(s)
- Administradores tienen acceso cross-branch
- Modelos protegidos: pacientes, historias, citas, claims, remesas, EDI, inventario

---

## Backup y Restauración

### Backup manual

```bash
make backup
# → backups/odoo_clinic_YYYYMMDD_HHMMSS.sql.gz
```

### Backup automático diario (recomendado para producción)

```bash
# Instalar cron job (se ejecuta diariamente a las 02:00 AM)
make backup-setup

# Verificar instalación
crontab -l | grep backup_cron

# Ver logs de backup
tail -f /var/log/cms_backup.log
```

El script `scripts/backup_cron.sh`:
- Crea backup comprimido en `backups/`
- Retiene los últimos **30 días** de backups (rotación automática)
- Registra resultado en `/var/log/cms_backup.log`

### RPO / RTO

| Métrica | Valor objetivo | Cómo lograrlo |
|---------|---------------|---------------|
| **RPO** (Recovery Point Objective) | 24 horas | Backup diario automático a las 02:00 |
| **RTO** (Recovery Time Objective) | < 2 horas | `make restore FILE=backups/latest.sql.gz` |

### Restore

```bash
# Listar backups disponibles
ls -lh backups/

# Restaurar backup específico
make restore FILE=backups/odoo_clinic_20260310_020000.sql.gz
```

> **Precaución**: El restore reemplaza toda la base de datos. Hacer un backup del estado actual antes de restaurar.

---

## Actualización de Módulos

```bash
# Parar Odoo
sudo docker compose stop odoo

# Correr update en contenedor temporal
sudo docker compose run --rm odoo odoo \
  --config=/etc/odoo/odoo.conf \
  --database=odoo_clinic \
  --update=clinic_core,clinic_patients,clinic_ehr \
  --stop-after-init

# Reiniciar Odoo
sudo docker compose start odoo
```

---

## Variables de Entorno

| Variable | Descripción | Default (NO usar en prod) |
|----------|-------------|--------------------------|
| `POSTGRES_DB` | Nombre de la base de datos | `odoo_clinic` |
| `POSTGRES_USER` | Usuario PostgreSQL | `odoo` |
| `POSTGRES_PASSWORD` | **CAMBIAR en producción** | `odoo_pwd_strong` |
| `POSTGRES_PORT` | Puerto externo PostgreSQL | `5433` |
| `ODOO_ADMIN_PASS` | **CAMBIAR en producción** | `admin_local_strong` |
| `TZ` | Zona horaria | `America/New_York` |
| `EDI_BASE_URL` | URL del clearinghouse | `http://mock-clearinghouse:18080` |
| `EDI_SFTP_HOST` | Host SFTP | `sftp-sandbox` |
| `EDI_SFTP_USER` | Usuario SFTP | `ediuser` |
| `EDI_SFTP_PASS` | **CAMBIAR en producción** | `edipass` |

---

## Puertos

| Servicio | Puerto externo | Puerto interno |
|----------|---------------|----------------|
| Odoo | **8070** | 8069 |
| Odoo Longpolling | **8073** | 8072 |
| Mock Clearinghouse | 18080 | 18080 |
| SFTP Sandbox | 2222 | 22 |
| pgAdmin | 5050 | 80 |
| PostgreSQL | **5433** | 5432 |

---

## Logs

```bash
make logs                               # Odoo logs (follow)
sudo docker compose logs mock-clearinghouse  # Clearinghouse logs
sudo docker compose logs sftp-sandbox        # SFTP logs
tail -f /var/log/cms_backup.log         # Backup cron logs
```

---

## Checklist Pre-Producción

- [ ] `.env` con contraseñas fuertes y únicas (no los defaults)
- [ ] `.env` NO está en git (verificar con `git status`)
- [ ] `odoo.prod.conf` montado en docker-compose (workers=4)
- [ ] `make backup-setup` ejecutado (backup diario activo)
- [ ] Primer backup manual verificado con `make backup`
- [ ] Restore probado en entorno de staging
- [ ] `list_db = False` en odoo.prod.conf
- [ ] RPO/RTO documentados y aceptados por el negocio
- [ ] Acceso SSH al servidor restringido (solo IPs autorizadas)
- [ ] Firewall: solo puertos 8070 y 18080 expuestos externamente
