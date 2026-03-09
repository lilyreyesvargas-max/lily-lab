# RUNBOOK.md — Procedimientos Operativos

## Inicio del Sistema

```bash
cd Clinical-Management-System
docker compose up -d
# Verificar estado:
docker compose ps
```

## Verificar Salud

```bash
# Odoo
curl -s http://localhost:8069/web/health | python3 -m json.tool

# Mock Clearinghouse
curl -s http://localhost:18080/health

# PostgreSQL
docker compose exec db pg_isready -U odoo
```

## Flujo de Trabajo Diario EDI

```bash
# 1. Generar claims 837P desde encuentros completados
make edi-generate-837

# 2. Enviar al clearinghouse mock
make edi-send-837

# 3. Importar pagos 835
# Copiar archivo 835 a edi/in/835/ luego:
make edi-import-835

# 4. Verificar elegibilidad de pacientes
make edi-request-eligibility
make edi-process-eligibility
```

## Troubleshooting

### Odoo no arranca
```bash
docker compose logs odoo | tail -50
# Problemas comunes: DB no disponible, módulo no instalado
docker compose restart db
docker compose restart odoo
```

### Mock Clearinghouse no responde
```bash
docker compose logs mock-clearinghouse
docker compose restart mock-clearinghouse
# Verificar: curl http://localhost:18080/health
```

### Error en módulo Odoo
```bash
# Ver logs
make logs

# Reinstalar módulo (reemplazar clinic_ehr con el módulo problemático)
docker compose exec odoo odoo --config=/etc/odoo/odoo.conf \
  --database=odoo_clinic --update=clinic_ehr --stop-after-init
```

### Error de permisos en archivos EDI
```bash
# Los archivos EDI deben ser accesibles por el contenedor odoo
chmod -R 777 ./edi/
```

### Base de datos corrupta
```bash
# Restore desde último backup
make restore FILE=backups/odoo_clinic_<latest>.sql.gz
```

## Reset Completo (DESTRUYE DATOS)

```bash
docker compose down -v  # Elimina volúmenes
docker compose up -d
make init
make load_demo
```

## Agregar Nueva Especialidad

1. Ir a Clinic > Configuration > Specialties
2. Crear nueva especialidad (ej. "Dermatología", code "DERM")
3. Asignar a la Clinic Config del branch correspondiente
4. Para formularios EHR específicos: crear módulo `ehr_derma` que hereda `clinic.ehr.encounter`

## Agregar Nuevo Médico

1. Crear usuario en Settings > Users
2. Asignar grupo "Clinic / Doctor"
3. En HR > Employees: crear empleado, asignar `clinic_role=doctor`, `specialty_id`
4. En clinic_appointments: crear `clinic.physician.schedule` para su branch y horario

## Monitoreo de Crons (Automation)

Ver ejecuciones en Clinic > Configuration > Automation Logs.
Los crons corren automáticamente:
- Recordatorios de citas: cada hora
- Alertas de stock: diariamente
- Alertas de expiración: diariamente
- Jobs EDI: diariamente (deshabilitado por defecto, habilitar en Automation Config)
