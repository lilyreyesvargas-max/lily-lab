# ADR-0003: Flowable como motor BPMN para V1

## Estado
Aceptada

## Contexto
Se necesita un motor de workflows BPMN embebido para gestionar procesos de negocio (aprobaciones, flujos de ventas). Opciones evaluadas:
1. **Flowable** (fork de Activiti, activo, Spring Boot starter)
2. **Camunda** (Platform 7 open source o Run)
3. **Desarrollo propio** (máquina de estados simple)

## Decisión
Se adopta **Flowable** con el Spring Boot Starter.

## Razones
- **Integración nativa** con Spring Boot 3.x vía `flowable-spring-boot-starter-process`.
- **Licencia Apache 2.0**: sin restricciones comerciales.
- **BPMN 2.0 completo**: soporte de user tasks, service tasks, gateways, timers, signals.
- **Auto-deploy**: los archivos `.bpmn20.xml` en classpath se despliegan automáticamente.
- **API Java rica**: RuntimeService, TaskService, HistoryService.
- **Comunidad activa** y documentación extensa.

## Consecuencias
- Flowable necesita su propio datasource apuntando al schema `platform` (compartido entre tenants).
- En `dev/local` se usa `flowable.database-schema-update=true`; en `prod` se migra manualmente.
- Las tareas de usuario se asignan por rol/usuario; la integración con el sistema de permisos se hace vía Spring Security.
- El proceso de ejemplo será "Aprobación de Venta" (`venta-aprobacion.bpmn20.xml`).
