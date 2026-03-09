# SECURITY.md — Configuración de Seguridad (HIPAA-Ready Local)

> **AVISO**: Esta configuración es para desarrollo/sandbox local. Para producción con PHI (Protected Health Information) real, se requieren controles adicionales de HIPAA.

## Grupos de Seguridad

| Grupo | Acceso |
|-------|--------|
| `Clinic / Administrator` | CRUD completo en todos los modelos clínicos |
| `Clinic / Doctor` | CRUD en EHR/Encounters, lectura en pacientes, citas propias |
| `Clinic / Nurse` | Lectura/escritura EHR e insumos; sin borrar |
| `Clinic / Receptionist` | Gestión de pacientes y citas; sin datos clínicos sensibles |
| `Clinic / Billing` | Solo lectura de datos clínicos; CRUD en claims y remesas |

## Separación Multi-Empresa

- Cada sucursal (S1, S2, S3) es una `res.company` separada
- Los médicos pertenecen a una sucursal; no comparten agenda entre sedes
- Los filtros `company_id` se aplican automáticamente en todos los modelos con `check_company=True`

## Consentimientos (HIPAA)

El módulo `clinic_patients` incluye el modelo `clinic.patient.consent` para registrar:
- Consentimiento de tratamiento
- Aviso de privacidad (HIPAA Notice of Privacy Practices)
- Autorización de facturación
- Consentimiento de investigación

## Credenciales Sandbox

Las credenciales en `.env.example` son **solo para sandbox local**. Cambiarlas antes de cualquier despliegue con datos reales:

```
POSTGRES_PASSWORD=<cambiar>
ODOO_ADMIN_PASS=<cambiar>
```

## Datos EDI

- Los archivos EDI se almacenan en `./edi/` — contienen PHI de prueba
- No incluir datos de pacientes reales en archivos de muestra
- El directorio `./edi/` está excluido de git (añadir a `.gitignore`)

## Recomendaciones para Producción

1. **HTTPS**: Configurar reverse proxy (nginx/traefik) con TLS
2. **Secretos**: Usar Docker Secrets o Vault, no variables de entorno en texto plano
3. **Cifrado en reposo**: Habilitar cifrado de volúmenes PostgreSQL
4. **Auditoría**: Activar `mail.tracking` y logging de Odoo en nivel INFO
5. **Backups**: Automatizar backups cifrados y almacenarlos fuera del servidor
6. **Red**: Aislar servicios en red Docker privada (ya configurado)
7. **Usuarios**: Deshabilitar usuario `admin` de Odoo; crear usuarios con roles específicos
8. **SFTP**: Usar claves SSH en lugar de contraseñas para el servidor SFTP real
