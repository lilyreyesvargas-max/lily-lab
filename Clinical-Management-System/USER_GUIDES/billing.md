# Guía de Usuario — Facturación / Billing

## Claims (Reclamaciones al Seguro)

### Crear un Claim
1. Ir a **Clinic > Billing > Claims**
2. Clic en **New**
3. Seleccionar: Paciente, Póliza de seguro, Fecha de servicio
4. En **Claim Lines**: agregar los servicios:
   - CPT Code (ej. 99213)
   - Descripción
   - Cantidad y precio unitario
   - % de cobertura del seguro
5. Los campos **Insurer Amount** y **Patient Amount** se calculan automáticamente
6. Clic en **Submit** para enviar el claim

### Estados del Claim
- **Draft** → editable
- **Submitted** → enviado al seguro (EDI)
- **Partial** → pago parcial recibido
- **Paid** → pagado completamente
- **Denied** → rechazado por el seguro
- **Voided** → anulado

### Procesar Remesas (ERA 835)

**Automático via EDI:**
```bash
# Copiar archivo 835 a edi/in/835/
cp archivo_era.txt edi/in/835/
make edi-import-835
```

**Manual en Odoo:**
1. Ir a **Clinic > Billing > Remittances**
2. Clic en **New**
3. Llenar: Aseguradora, Fecha de pago, Número de cheque/EFT, Total pagado
4. En líneas: vincular a claims individuales
5. Cambiar estado a **Processed** → **Reconciled**

## Reglas de Cobertura

1. Ir a **Clinic > Billing > Coverage Rules**
2. Crear reglas por aseguradora/plan/código CPT
3. Definir % de cobertura, copago del paciente, si requiere autorización previa

## Verificación de Elegibilidad

1. Ir a **Clinic > EDI US > Eligibility Requests**
2. Crear nueva solicitud con Paciente + Aseguradora
3. Clic en **Send 270 Request**
4. El sistema envía al clearinghouse y recibe respuesta 271
5. Ver resultado: elegible/no elegible, plan, deducible

## Reportes EDI

Ver todas las transacciones EDI en **Clinic > EDI US > Transactions**:
- Filtrar por tipo (837/835/270/271) y estado
- Reenviar transacciones en error
