# Guía de Usuario — Médico

## Historia Clínica Electrónica (EHR)

### Crear Nuevo Encounter (Consulta)
1. Ir a **Clinic > EHR > Encounters**
2. Clic en **New**
3. Seleccionar: Paciente, Especialidad, Fecha
4. Llenar pestaña **Clinical Notes**:
   - Chief Complaint (queja principal)
   - HPI (Historia de la enfermedad actual)
   - Physical Examination (Examen físico)
   - Assessment (Evaluación)
   - Plan
5. En pestaña **Diagnoses**: agregar códigos ICD-10
6. Clic en **Start** → estado "In Progress"
7. Al terminar: clic en **Complete**

### Campos por Especialidad

**Medicina General** (pestaña "Vital Signs"):
- TA Sistólica/Diastólica, Frecuencia cardíaca
- Temperatura, Peso (kg), Talla (cm) → calcula IMC automáticamente
- Saturación de oxígeno

**Ginecología** (pestaña "Gynecology"):
- FUM (Última menstruación) → calcula edad gestacional automáticamente
- Gesta, Para, Abortos

**Oftalmología** (pestaña "Ophthalmology"):
- Refracción OD/OI: Esfera, Cilindro, Eje
- Agudeza visual OD/OI
- PIO (Presión intraocular) OD/OI

**Estomatología** (pestaña "Stomatology"):
- Odontograma JSON (para integraciones futuras)
- Procedimiento dental, notas periodontales

### Agregar Diagnóstico ICD-10
1. En pestaña **Diagnoses** del encounter
2. Clic en "Add a line"
3. Buscar código ICD-10 por código o descripción
4. Seleccionar tipo: Primary/Secondary/Tertiary

### Ver Historial del Paciente
1. Desde el registro del paciente: ir a sus encounters
2. O buscar en EHR > Encounters, filtrar por paciente

## Citas Propias
- Ver agenda en **Clinic > Appointments** con vista Calendar
- Clic en cita → **Start** para iniciar consulta
- Al finalizar → **Complete** (crea encounter vinculado si está instalado clinic_ehr)
