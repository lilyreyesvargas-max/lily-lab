# Revision Arquitectonica: Platform

**Baseline Revisado**: `docs/architecture-baseline.md`
**Fecha de Revision**: 2026-03-06
**Revisor**: Architecture Review Agent
**Modelo de Revision**: Claude Opus 4.6 (Analisis Profundo)

**Evaluacion General**: ACEPTABLE CON REVISIONES OBLIGATORIAS

---

## Resumen Ejecutivo

**Veredicto**: La plataforma multi-tenant presenta una base arquitectonica solida para un V1 de modular monolith. La estructura de modulos Maven, el aislamiento por schema, el patron outbox para eventos de dominio y la integracion con Flowable demuestran buen criterio arquitectonico. Sin embargo, existen **4 problemas criticos** que deben resolverse antes de cualquier despliegue en produccion: credenciales hardcodeadas, ausencia total de autorizacion server-side en la UI ZK, leak de recursos JDBC en el connection provider multi-tenant, y un error latente de Flowable con el schema de sus tablas. Ademas, hay **6 problemas de severidad alta** que impactan la fiabilidad operativa y la mantenibilidad a mediano plazo.

**Fortalezas Clave** (Top 3):
1. Aislamiento multi-tenant por schema bien implementado con validacion estricta de nombres de tenant via regex, previniendo SQL injection en SET search_path
2. Patron Transactional Outbox correctamente implementado con SPI para publisher externo, preparado para evolucion a Kafka sin cambios en modulos de negocio
3. Estructura modular Maven disciplinada con 20 sub-modulos y dependencias explicitas que respetan las capas (shared-libs -> core -> modules -> ui)

**Problemas Criticos** (Resolver antes de produccion):
1. **CRITICO** - TD-01: Credenciales admin `platformadmin/admin123` hardcodeadas y logueadas en texto plano en cada arranque
2. **CRITICO** - TD-02: ZK UI SecurityFilterChain usa `permitAll()` sin autorizacion server-side; cualquier sesion HTTP valida accede a todas las operaciones
3. **CRITICO** - TD-04: Leak de `Statement` objects en `SchemaMultiTenantConnectionProvider` en cada operacion JPA (lineas 72 y 87)
4. **CRITICO** - Flowable `act_ru_job` tables: riesgo de que Flowable no encuentre sus tablas si el search_path no incluye el schema `platform` durante la ejecucion del async executor

**Problemas Altos** (Resolver en V1):
1. **ALTO** - TD-13: `ui-zk-app` depende directamente de `mod-customers` y `mod-employees`, violando ADR-0002
2. **ALTO** - TD-15: `OutboxScheduler` itera tenants desde YAML en lugar de BD; tenants creados en runtime no procesan eventos
3. **ALTO** - TD-20: Ausencia de tests de integracion end-to-end a pesar de que Testcontainers ya esta declarado
4. **ALTO** - `LayoutVM.init()` establece `TenantContext` sin limpiarlo en un `finally`, causando leak de ThreadLocal entre requests ZK
5. **ALTO** - `FlowableMultiTenantConfig` lee tenants desde YAML (`app.tenants`) en lugar de BD, misma inconsistencia que TD-15
6. **ALTO** - `ModulesHealthIndicator.health()` reporta `UP` incluso cuando hay modulos `NOT_LOADED` (linea 45)

**Recomendacion**: Aprobar con condiciones -- resolver los 4 problemas criticos y los 6 altos antes del primer despliegue a un entorno accesible por usuarios reales.

---

## Lista Priorizada de Problemas

### P1 - CRITICO: Credenciales Hardcodeadas del Platform Admin (TD-01)

**Archivo**: `/home/lily/eclipse-workspace/lily-lab/platform/core/tenancy/src/main/java/com/lreyes/platform/core/tenancy/platform/PlatformUserService.java`
**Lineas**: 97-101

```java
public void seedDefaultAdmin() {
    if (!userRepo.existsByUsername("platformadmin")) {
        create("platformadmin", "admin123", "admin@platform.local", "Platform Administrator");
        log.info("Platform admin por defecto creado: platformadmin / admin123");
    }
}
```

**Descripcion Exacta del Problema**:
El metodo `seedDefaultAdmin()` crea un usuario `platformadmin` con password `admin123` en cada arranque de la aplicacion. Este metodo se invoca desde `TenantBootstrapRunner.run()` (linea 44) sin ninguna condicion de perfil. En produccion, este usuario existe con credenciales conocidas por cualquiera que lea el codigo fuente. Ademas, la linea 100 escribe las credenciales en texto plano al log con nivel INFO, lo que las expone en cualquier sistema de agregacion de logs.

**Impacto**: Un atacante con acceso al codigo fuente (o al repo Git) conoce las credenciales del superadmin de la plataforma completa. Con el rol `platform_admin` puede acceder a todos los tenants, todos los datos, y gestionar schemas. La gravedad se amplifica porque el platform admin en la UI ZK tiene acceso irrestricto via `permitAll()` (ver P2).

**Severidad**: CRITICO

**Solucion Propuesta**:

1. Leer credenciales del seed desde variables de entorno:
```java
public void seedDefaultAdmin() {
    String username = System.getenv().getOrDefault("PLATFORM_ADMIN_USER", "platformadmin");
    String password = System.getenv("PLATFORM_ADMIN_PASSWORD");
    if (password == null || password.isBlank()) {
        log.warn("PLATFORM_ADMIN_PASSWORD no definido. Seed de admin omitido.");
        return;
    }
    if (!userRepo.existsByUsername(username)) {
        create(username, password, "admin@platform.local", "Platform Administrator");
        log.info("Platform admin por defecto creado: {}", username);
        // NUNCA loguear la password
    }
}
```

2. Agregar condicion `@Profile("!prod")` en `TenantBootstrapRunner` o un flag `app.seed.enabled=false` para produccion.

3. En `application-prod.yml`, desactivar el seed explicitamente.

**Agente responsable**: `zk-bug-solver` (fix de seguridad).
**Esfuerzo estimado**: 1-2 horas.
**Implementable ahora**: Si.

---

### P2 - CRITICO: ZK UI Sin Autorizacion Server-Side (TD-02)

**Archivo**: `/home/lily/eclipse-workspace/lily-lab/platform/core/auth-security/src/main/java/com/lreyes/platform/core/authsecurity/SecurityConfig.java`
**Linea**: 67

```java
.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
```

**Archivos Afectados Adicionales**:
- `/home/lily/eclipse-workspace/lily-lab/platform/ui/zk-app/src/main/java/com/lreyes/platform/ui/zk/vm/LayoutVM.java` (lineas 88-93): Unica proteccion es leer `UiUser` de la sesion HTTP y redirigir a login si es null.
- `/home/lily/eclipse-workspace/lily-lab/platform/ui/zk-app/src/main/java/com/lreyes/platform/ui/zk/vm/CustomerListVM.java` (linea 40-41): No valida roles antes de operar.
- Todos los ViewModels en `ui/zk-app/src/main/java/.../vm/`.

**Descripcion Exacta del Problema**:
La SecurityFilterChain para ZK (Order=1) aplica `permitAll()` a todas las rutas `/zul/**`, `/zkau/**`, `/login`, `/`. Esto significa que Spring Security no interviene en absoluto para requests ZK. La unica "proteccion" es que `LayoutVM.init()` verifica si existe un objeto `UiUser` en la sesion HTTP (linea 89-93) y redirige a login si no existe. Pero esto tiene multiples problemas:

- **Sin verificacion de roles en operaciones**: `CustomerListVM.save()` (linea 89) ejecuta `customerService.create()` sin verificar que el usuario tiene permiso para crear clientes. Cualquier usuario autenticado con sesion valida puede ejecutar cualquier operacion CRUD.
- **Sin CSRF**: `csrf.disable()` en linea 64. Aunque ZK tiene su propia proteccion CSRF (desktop ID), la cadena de Spring no aporta nada.
- **Falsificacion de sesion**: Un atacante que obtenga un JSESSIONID valido (por ejemplo via XSS o sniffing en red sin HTTPS) tiene acceso completo sin restriccion de rol.
- **Platform admin sin verificacion en VMs de negocio**: Los ViewModels de administracion de plataforma (`TenantListVM`, `PlatformUserListVM`) dependen del check `user.isPlatformAdmin()` en el buildMenu de `LayoutVM` para mostrar/ocultar menus, pero esto es seguridad por oscuridad (UI-only). Un request directo a `/zul/platform/tenants.zul` con una sesion de usuario regular no tendria bloqueo server-side.

**Severidad**: CRITICO

**Solucion Propuesta**:

Fase 1 (inmediata): Agregar un interceptor/filtro ZK que valide roles server-side:

```java
// En cada ViewModel que requiera un rol especifico, agregar al inicio de @Init:
private void requireRole(String... roles) {
    UiUser user = (UiUser) Sessions.getCurrent().getAttribute("user");
    if (user == null) {
        Executions.sendRedirect("/zul/login.zul");
        return;
    }
    boolean hasRole = Arrays.stream(roles).anyMatch(user::hasRole);
    if (!hasRole && !user.isPlatformAdmin()) {
        throw new SecurityException("Acceso denegado: se requiere rol " + Arrays.toString(roles));
    }
}
```

Fase 2 (corto plazo): Implementar un `ExecutionInit` listener de ZK que intercepte todas las cargas de pagina y valide roles contra una tabla de permisos:

```java
public class ZkSecurityInit implements ExecutionInit {
    @Override
    public void init(Execution exec, Execution parent) {
        if (parent != null) return; // solo primer request
        String page = exec.getDesktop().getRequestPath();
        UiUser user = (UiUser) Sessions.getCurrent().getAttribute("user");
        if (user == null && !page.contains("login")) {
            exec.sendRedirect("/zul/login.zul");
        }
        // Validar permisos de pagina contra user.getRoles()
    }
}
```

**Agente responsable**: `zk-developer` (implementacion del filtro ZK), `zk-bug-solver` (hardening).
**Esfuerzo estimado**: 4-8 horas.
**Implementable ahora**: Si (Fase 1). Fase 2 requiere disenar la tabla de permisos pagina-rol.

---

### P3 - CRITICO: Leak de Statement Objects en SchemaMultiTenantConnectionProvider (TD-04)

**Archivo**: `/home/lily/eclipse-workspace/lily-lab/platform/core/tenancy/src/main/java/com/lreyes/platform/core/tenancy/SchemaMultiTenantConnectionProvider.java`
**Lineas**: 72, 85, 87

```java
// Linea 72 - en setSchema():
connection.createStatement().execute(sql);

// Linea 85 - en resetSchema() (rama H2):
connection.createStatement().execute("SET SCHEMA \"PUBLIC\"");

// Linea 87 - en resetSchema() (rama PostgreSQL):
connection.createStatement().execute("RESET search_path");
```

**Descripcion Exacta del Problema**:
Cada llamada a `connection.createStatement()` crea un nuevo objeto `Statement` JDBC. Segun el estandar JDBC, los Statement deben cerrarse explicitamente cuando ya no se usan. En este codigo, el Statement se crea, se ejecuta, y su referencia se pierde inmediatamente -- nunca se cierra.

Esto ocurre en **cada operacion JPA**: `getConnection()` llama a `setSchema()` (1 Statement leaked) y `releaseConnection()` llama a `resetSchema()` (1 Statement leaked). Es decir, **2 Statements se filtran por cada transaccion JPA**.

En un sistema con carga moderada (100 requests/segundo), esto produce ~200 Statements/segundo que no se cierran. Aunque PostgreSQL y HikariCP eventualmente limpian los recursos cuando la conexion se devuelve al pool, el acumulo dentro de una transaccion larga o bajo carga sostenida puede causar:
- Agotamiento de memoria en el servidor de base de datos
- Warnings/errores de "too many open statements" en el driver JDBC
- Degradacion progresiva del rendimiento

**Severidad**: CRITICO

**Solucion Propuesta**:

```java
private void setSchema(Connection connection, String schema) throws SQLException {
    String dbProduct = connection.getMetaData().getDatabaseProductName();
    boolean isH2 = dbProduct.toLowerCase().contains("h2");
    String resolvedSchema = isH2 ? schema.toUpperCase() : schema;
    String sql = isH2
            ? "SET SCHEMA \"" + resolvedSchema + "\""
            : "SET search_path TO " + resolvedSchema;
    try (Statement stmt = connection.createStatement()) {
        stmt.execute(sql);
    }
}

private void resetSchema(Connection connection) throws SQLException {
    String dbProduct = connection.getMetaData().getDatabaseProductName();
    boolean isH2 = dbProduct.toLowerCase().contains("h2");
    String sql = isH2 ? "SET SCHEMA \"PUBLIC\"" : "RESET search_path";
    try (Statement stmt = connection.createStatement()) {
        stmt.execute(sql);
    }
}
```

Adicionalmente, se recomienda cachear el resultado de `connection.getMetaData().getDatabaseProductName()` en un campo estatico para evitar la llamada a metadatos en cada operacion.

**Agente responsable**: `zk-bug-solver`.
**Esfuerzo estimado**: 30 minutos.
**Implementable ahora**: Si. Es un fix trivial.

---

### P4 - CRITICO: Flowable Act Tables No Encontradas en Schema Correcto

**Archivos**:
- `/home/lily/eclipse-workspace/lily-lab/platform/core/workflow/src/main/java/com/lreyes/platform/core/workflow/FlowableDatasourceConfig.java` (lineas 30-49)
- `/home/lily/eclipse-workspace/lily-lab/platform/core/tenancy/src/main/java/com/lreyes/platform/core/tenancy/SchemaMultiTenantConnectionProvider.java` (linea 87)
- `/home/lily/eclipse-workspace/lily-lab/platform/platform-app/src/main/resources/application-dev.yml` (lineas 30-35)

**Descripcion Exacta del Problema**:
Flowable usa un DataSource separado configurado con `?currentSchema=platform` en la URL JDBC (application-dev.yml, linea 32). Sin embargo, hay un riesgo latente en la interaccion entre el connection provider multi-tenant y Flowable:

1. `FlowableDatasourceConfig` crea un `HikariDataSource` independiente con la URL que incluye `currentSchema=platform`. Esto es correcto para el pool de Flowable.

2. Sin embargo, cuando Flowable ejecuta jobs asincronos (incluso aunque `async-executor-activate=false` en dev), el `FlowableMultiTenantConfig` despliega procesos usando el `RepositoryService` inyectado por Spring. Si durante el bootstrap el `search_path` de la conexion primaria esta apuntando a un schema de tenant (porque `TenantBootstrapRunner` esta iterando tenants), y Flowable internamente usa la conexion del pool primario en lugar de su pool separado para alguna operacion, las tablas `act_*` no se encontrarian en el schema del tenant.

3. El comentario en `resetSchema()` (linea 77-79) confirma que este problema ya se manifesto: "Usar RESET en vez de SET search_path TO public evita perder '$user' del path, lo cual causaria que otros componentes (ej. Flowable) no encuentren tablas en el schema del usuario de conexion." Esto indica que Flowable ha tenido problemas para encontrar sus tablas.

4. En el perfil `prod`, `flowable.database-schema-update=false` (application-prod.yml, linea 45). Si Flowable no puede encontrar sus tablas en produccion, fallara silenciosamente o lanzara excepciones al intentar ejecutar queries contra `act_ru_job`, `act_ru_task`, etc.

5. El `DataSourceTransactionManager` creado en linea 47 de `FlowableDatasourceConfig` no esta coordinado con el `PlatformTransactionManager` principal de Spring. Esto significa que las operaciones de Flowable y las operaciones JPA de negocio NO participan en la misma transaccion, lo que puede causar inconsistencias si `SalesService.create()` falla despues de iniciar el proceso Flowable (lineas 65-78 de SalesService).

**Severidad**: CRITICO

**Solucion Propuesta**:

1. **Verificar aislamiento de DataSources**: Agregar un test de integracion que arranque la aplicacion completa con Testcontainers y verifique que Flowable opera correctamente con sus tablas en el schema `platform` mientras las operaciones de negocio usan schemas de tenant.

2. **Documentar la no-transaccionalidad**: Entre la creacion del SalesOrder (JPA, schema del tenant) y el inicio del proceso Flowable (Flowable DataSource, schema platform), NO hay transaccion distribuida. Si el `startProcess` tiene exito pero el `save` posterior falla, quedara un proceso huerfano en Flowable. Agregar un mecanismo de compensacion o al menos un log de warning.

3. **Validar en produccion**: Antes de desplegar con `database-schema-update=false`, ejecutar la creacion de tablas Flowable via un script SQL dedicado contra el schema `platform`, o cambiar a `database-schema-update=true` en el primer despliegue.

**Agente responsable**: `zk-developer` (test de integracion), `zk-bug-solver` (compensacion transaccional).
**Esfuerzo estimado**: 8-16 horas.
**Implementable ahora**: Parcialmente. El test de integracion se puede escribir ahora. La compensacion transaccional requiere disenar el mecanismo.

---

### P5 - ALTO: ui-zk-app Depende Directamente de Modulos de Negocio (TD-13)

**Archivo**: `/home/lily/eclipse-workspace/lily-lab/platform/ui/zk-app/pom.xml`
**Lineas**: 60-65

```xml
<dependency>
    <groupId>com.lreyes.platform</groupId>
    <artifactId>mod-customers</artifactId>
</dependency>
<dependency>
    <groupId>com.lreyes.platform</groupId>
    <artifactId>mod-employees</artifactId>
</dependency>
```

**Archivos Afectados Adicionales**:
- `/home/lily/eclipse-workspace/lily-lab/platform/ui/zk-app/src/main/java/com/lreyes/platform/ui/zk/vm/CustomerListVM.java` (linea 4): `import com.lreyes.platform.modules.customers.CustomerService`
- `/home/lily/eclipse-workspace/lily-lab/platform/ui/zk-app/src/main/java/com/lreyes/platform/ui/zk/vm/EmployeeListVM.java` (lineas 7-9): imports directos de `EmployeeService`, DTOs

**Descripcion Exacta del Problema**:
ADR-0002 establece que el modular monolith debe tener modulos con dependencias explicitas que no accedan a internals de otros modulos. Sin embargo, `ui-zk-app` importa directamente `CustomerService`, `EmployeeService`, sus DTOs, y en el caso de `EmployeeListVM` incluso `UserRepository` (linea 6). Esto crea acoplamiento directo entre la capa UI y la implementacion de los modulos de negocio.

Consecuencias concretas:
- No es posible extraer `mod-customers` como microservicio sin reescribir todos los ViewModels que lo usan.
- Un cambio en `CustomerService` (firma de metodo, nombre de DTO) rompe la compilacion de `ui-zk-app`.
- `EmployeeListVM` accede directamente a `UserRepository` (linea 128-129), saltandose la capa de servicio.

**Severidad**: ALTO

**Solucion Propuesta**:

Opcion A (corto plazo): Definir interfaces de servicio en `shared-libs` o en un nuevo modulo `module-api` que contenga solo los contratos (interfaces + DTOs). Los modulos implementan esas interfaces, y `ui-zk-app` depende solo de `module-api`.

Opcion B (inmediata, menos ideal): Documentar la decision como excepcion aceptada para V1, pero agregar un test de ArchUnit que enumere y limite las dependencias permitidas de `ui.zk` hacia `modules.*`.

Para `EmployeeListVM` linea 128: reemplazar el acceso directo a `UserRepository` por una llamada a `UserService`, respetando la capa de servicio.

**Agente responsable**: `zk-developer` (refactor de imports), `zk-ui-designer` (validar que la UI no se rompe).
**Esfuerzo estimado**: 8-16 horas para Opcion A, 2 horas para Opcion B.
**Implementable ahora**: Opcion B si. Opcion A requiere mas disenio.

---

### P6 - ALTO: OutboxScheduler Usa Lista YAML en Lugar de BD (TD-15)

**Archivo**: `/home/lily/eclipse-workspace/lily-lab/platform/core/events/src/main/java/com/lreyes/platform/core/events/OutboxScheduler.java`
**Linea**: 28

```java
for (String tenant : tenantProperties.getTenants()) {
```

**Descripcion Exacta del Problema**:
`OutboxScheduler` itera la lista de tenants obtenida de `TenantProperties.getTenants()`, que lee directamente del YAML (`app.tenants`). Sin embargo, `TenantBootstrapRunner` (lineas 53-54) lee tenants activos desde la base de datos via `tenantRegistryService.getActiveTenantNames()`. Si un tenant se crea en runtime (via la API REST o la UI de platform admin), no aparecera en el YAML, y por lo tanto sus eventos outbox NUNCA se procesaran.

Esto se agrava porque el patron outbox es el mecanismo principal para garantizar entrega de eventos de dominio. Si un tenant nuevo crea clientes, pedidos, etc., los eventos se guardan en `outbox_events` pero nunca se procesan para publicacion externa.

La misma inconsistencia existe en `FlowableMultiTenantConfig` (linea 32-33) que tambien lee de `app.tenants` via `Binder.get(env)`.

**Severidad**: ALTO

**Solucion Propuesta**:

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxScheduler {

    private static final int BATCH_SIZE = 50;

    private final OutboxService outboxService;
    private final TenantRegistryService tenantRegistryService; // Cambiar de TenantProperties

    @Scheduled(fixedDelayString = "${app.events.outbox-poll-ms:10000}")
    public void processOutbox() {
        List<String> activeTenants = tenantRegistryService.getActiveTenantNames();
        for (String tenant : activeTenants) {
            // ... resto igual
        }
    }
}
```

Para `FlowableMultiTenantConfig`, agregar un mecanismo que re-despliegue procesos BPMN cuando se crea un nuevo tenant. Esto puede ser un evento de dominio `TenantCreatedEvent` que dispare el despliegue.

**Agente responsable**: `zk-bug-solver`.
**Esfuerzo estimado**: 2-3 horas.
**Implementable ahora**: Si.

---

### P7 - ALTO: Ausencia de Tests de Integracion (TD-20)

**Archivos de Test Existentes** (21 archivos):
- Tests unitarios con mocks en `core/auth-security/src/test/`, `core/events/src/test/`, `modules/*/src/test/`
- Un solo test de integracion parcial: `core/tenancy/src/test/java/.../TenancyMigrationIT.java` -- valida migraciones Flyway pero NO arranca el Spring ApplicationContext completo

**Descripcion Exacta del Problema**:
El proyecto declara `testcontainers` 1.19.3 como dependencia pero no tiene tests de integracion que:
- Arranquen la aplicacion Spring Boot completa con PostgreSQL real
- Validen el flujo multi-tenant end-to-end (crear tenant -> migrar -> crear datos -> verificar aislamiento)
- Validen el flujo de Flowable (crear pedido -> verificar proceso iniciado -> completar tarea)
- Validen la seguridad (JWT authentication -> tenant resolution -> autorizacion)
- Validen el outbox (crear entidad -> verificar evento en outbox -> procesar outbox)

`TenancyMigrationIT.java` es un buen inicio pero solo valida la capa de migraciones SQL, no la aplicacion.

**Severidad**: ALTO

**Solucion Propuesta**:

Crear al menos estos tests de integracion:

1. `PlatformBootstrapIT`: Arranca `@SpringBootTest` con Testcontainers PostgreSQL. Verifica que el bootstrap completo funciona: schema `platform` migrado, tenants migrados, admin creado, Flowable deployments hechos.

2. `MultiTenantIsolationIT`: Crea datos en tenant A, verifica que NO aparecen en tenant B. Valida que `TenantContext` se propaga correctamente a traves de la cadena Service -> Repository -> Hibernate.

3. `SalesWorkflowIT`: Crea un SalesOrder, verifica que el proceso Flowable se inicia, reclama la tarea, la completa, y verifica el cambio de estado.

4. `SecurityIT`: Verifica que endpoints protegidos requieren JWT valido, que el JWT sin tenant_id es rechazado, que roles incorrectos dan 403.

**Agente responsable**: `zk-developer` (escribir los tests).
**Esfuerzo estimado**: 16-24 horas.
**Implementable ahora**: Si. La infraestructura (Testcontainers) ya esta declarada.

---

### P8 - ALTO: TenantContext Leak en LayoutVM

**Archivo**: `/home/lily/eclipse-workspace/lily-lab/platform/ui/zk-app/src/main/java/com/lreyes/platform/ui/zk/vm/LayoutVM.java`
**Lineas**: 125-127

```java
// Establecer tenant en contexto
if (currentTenant != null) {
    TenantContext.setCurrentTenant(currentTenant);
}
```

**Linea adicional**: 198
```java
TenantContext.setCurrentTenant(currentTenant);
```

**Descripcion Exacta del Problema**:
`LayoutVM.init()` establece `TenantContext.setCurrentTenant()` en las lineas 126 y 198, pero NUNCA lo limpia. A diferencia del `TenantFilter` (que limpia en su bloque `finally`, linea 66) y del `LoginVM.loginTenantUser()` (que limpia en su bloque `finally`, linea 181), el `LayoutVM` asume que el TenantContext permanecera establecido para toda la sesion ZK.

Esto funciona por casualidad en el servidor ZK porque cada desktop ZK recrea el contexto en cada request AU (AJAX Update). Pero si un hilo del pool de threads se reutiliza para una operacion REST API despues de servir un request ZK, el TenantContext del usuario ZK anterior podria permanecer activo, causando que el request REST API opere en el schema de otro tenant.

El mismo patron se repite en todos los ViewModels de negocio:
- `CustomerListVM.loadData()` (linea 46): `TenantContext.setCurrentTenant(user.getTenantId())` sin finally
- `CustomerListVM.save()` (linea 95): mismo problema
- `EmployeeListVM.loadData()` (linea 51): mismo problema
- `EmployeeListVM.save()` (linea 110): mismo problema

**Severidad**: ALTO

**Solucion Propuesta**:

Crear un metodo utilitario en un BaseVM:

```java
protected <T> T withTenant(Supplier<T> action) {
    try {
        TenantContext.setCurrentTenant(user.getTenantId());
        return action.get();
    } finally {
        TenantContext.clear();
    }
}

protected void withTenantVoid(Runnable action) {
    try {
        TenantContext.setCurrentTenant(user.getTenantId());
        action.run();
    } finally {
        TenantContext.clear();
    }
}
```

Uso: `withTenantVoid(() -> { customerService.create(...); });`

**Agente responsable**: `zk-developer` (crear BaseVM), `zk-bug-solver` (aplicar en todos los VMs).
**Esfuerzo estimado**: 4-6 horas.
**Implementable ahora**: Si.

---

### P9 - ALTO: FlowableMultiTenantConfig Lee Tenants Desde YAML

**Archivo**: `/home/lily/eclipse-workspace/lily-lab/platform/core/workflow/src/main/java/com/lreyes/platform/core/workflow/FlowableMultiTenantConfig.java`
**Lineas**: 32-34

```java
List<String> tenants = Binder.get(env)
        .bind("app.tenants", Bindable.listOf(String.class))
        .orElse(List.of());
```

**Descripcion Exacta del Problema**:
Misma raiz que TD-15. El `ApplicationRunner` que despliega procesos BPMN por tenant lee la lista de tenants directamente del Environment (que viene del YAML). Si se crean tenants nuevos via la UI o API, estos no tendran procesos BPMN desplegados, y cualquier intento de iniciar un workflow para ellos fallara con "no process definition found for key 'venta-aprobacion' and tenantId '<nuevo-tenant>'".

**Severidad**: ALTO

**Solucion Propuesta**:

Inyectar `TenantRegistryService` en lugar de leer del `Environment`:

```java
@Bean
ApplicationRunner deployProcessesPerTenant(
        RepositoryService repositoryService,
        TenantRegistryService tenantRegistryService) {
    return args -> {
        List<String> tenants = tenantRegistryService.getActiveTenantNames();
        // ... resto igual
    };
}
```

Ademas, crear un metodo publico `deployForTenant(String tenantId)` que pueda invocarse cuando se crea un nuevo tenant en runtime.

**Agente responsable**: `zk-bug-solver`.
**Esfuerzo estimado**: 2-3 horas.
**Implementable ahora**: Si.

---

### P10 - ALTO: ModulesHealthIndicator Siempre Reporta UP (TD-23)

**Archivo**: `/home/lily/eclipse-workspace/lily-lab/platform/platform-app/src/main/java/com/lreyes/platform/config/ModulesHealthIndicator.java`
**Linea**: 45

```java
Health.Builder builder = allUp ? Health.up() : Health.up();
```

**Descripcion Exacta del Problema**:
El ternario en la linea 45 evalua `Health.up()` en AMBAS ramas. Cuando `allUp` es `false` (algun modulo no esta cargado), deberia usar `Health.down()` o `Health.degraded()`. Esto es un bug evidente de copy-paste.

El impacto es que el health check de Spring Actuator (`/actuator/health`) siempre reportara status `UP` para el componente `modules`, incluso si modulos criticos como `salesService` o `workflowService` no estan presentes. Cualquier sistema de monitoreo o load balancer que dependa del health check no detectara la degradacion.

**Severidad**: ALTO

**Solucion Propuesta**:

```java
Health.Builder builder = allUp ? Health.up() : Health.down();
```

**Agente responsable**: `zk-bug-solver`.
**Esfuerzo estimado**: 5 minutos.
**Implementable ahora**: Si. Es un fix de una linea.

---

### P11 - MEDIO: BCryptPasswordEncoder Instanciado Directamente (TD-08)

**Archivos**:
- `/home/lily/eclipse-workspace/lily-lab/platform/core/tenancy/src/main/java/com/lreyes/platform/core/tenancy/UserService.java` (linea 23)
- `/home/lily/eclipse-workspace/lily-lab/platform/core/tenancy/src/main/java/com/lreyes/platform/core/tenancy/platform/PlatformUserService.java` (linea 21)

```java
private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
```

**Descripcion Exacta del Problema**:
Ambos servicios crean su propia instancia de `BCryptPasswordEncoder` con `new`. Esto tiene varias consecuencias:
- No es configurable (no se puede cambiar el strength/rounds de BCrypt via configuracion).
- Cada servicio tiene su propia instancia en lugar de compartir un bean Spring.
- El campo se declara `final` pero no se inyecta via constructor (Lombok `@RequiredArgsConstructor` no lo incluye porque ya esta inicializado).
- Si se quisiera migrar a otra estrategia de hashing (Argon2, SCrypt), habria que modificar ambas clases.

**Severidad**: MEDIO

**Solucion Propuesta**:

Declarar un bean `PasswordEncoder` en la configuracion de seguridad y usar inyeccion de dependencias:

```java
// En SecurityConfig o una clase @Configuration dedicada:
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

Luego inyectar en ambos servicios via constructor.

**Agente responsable**: `zk-developer`.
**Esfuerzo estimado**: 1 hora.
**Implementable ahora**: Si.

---

### P12 - MEDIO: JwtDecoder Lanza Excepcion en Modo OIDC (TD-21)

**Archivo**: `/home/lily/eclipse-workspace/lily-lab/platform/core/auth-security/src/main/java/com/lreyes/platform/core/authsecurity/SecurityConfig.java`
**Lineas**: 128-143

```java
@Bean
public JwtDecoder jwtDecoder() {
    if (securityProperties.isJwtLocal()) {
        // ... crea decoder HS256
    }
    // En modo OIDC, Spring Boot auto-configura el decoder.
    throw new IllegalStateException(
            "En modo OIDC, configure spring.security.oauth2.resourceserver.jwt.issuer-uri. ...");
}
```

**Descripcion Exacta del Problema**:
Este bean siempre se registra (no tiene `@ConditionalOnProperty`). En modo OIDC, si Spring Boot intenta crear este bean antes de que la auto-configuracion de OAuth2 Resource Server cree el suyo, lanzara una `IllegalStateException` que impedira el arranque de la aplicacion.

La logica depende del orden de creacion de beans, que en Spring no esta garantizado a menos que se use `@DependsOn` o `@ConditionalOnMissingBean`. Es un diserio fragil.

**Severidad**: MEDIO

**Solucion Propuesta**:

```java
@Bean
@ConditionalOnProperty(name = "app.security.mode", havingValue = "jwt-local")
public JwtDecoder jwtDecoder() {
    SecretKey key = new SecretKeySpec(
            securityProperties.getJwtSecret().getBytes(), "HmacSHA256");
    return NimbusJwtDecoder.withSecretKey(key).build();
}
```

Aplicar el mismo patron al bean `devJwtService()` (que ya retorna `null` en modo no jwt-local, lo cual puede causar NPE).

**Agente responsable**: `zk-bug-solver`.
**Esfuerzo estimado**: 1 hora.
**Implementable ahora**: Si.

---

### P13 - MEDIO: SalesService Acoplamiento Directo con WorkflowService (TD-14)

**Archivo**: `/home/lily/eclipse-workspace/lily-lab/platform/modules/sales/src/main/java/com/lreyes/platform/modules/sales/SalesService.java`
**Lineas**: 31, 65-76

```java
private final WorkflowService workflowService;
// ...
String processId = workflowService.startProcess(
        "venta-aprobacion", tenantId, saved.getOrderNumber(), Map.of(...));
```

**Descripcion Exacta del Problema**:
`SalesService` en el modulo `modules/sales` depende directamente de `WorkflowService` en `core/workflow`. Esto es un acoplamiento fuerte que impide:
- Ejecutar el modulo de ventas sin el modulo de workflow
- Testear SalesService sin mockear WorkflowService
- Extraer el modulo de ventas como servicio independiente sin refactorizar

Ademas, el nombre del proceso BPMN (`"venta-aprobacion"`) esta hardcodeado como string magico. Si el nombre del proceso cambia en el archivo BPMN, el codigo de SalesService falla en runtime sin warning en compilacion.

**Severidad**: MEDIO

**Solucion Propuesta**:

Introducir un evento de dominio `OrderCreatedEvent` (que ya existe) y mover el inicio del proceso a un listener de eventos:

```java
@Component
@RequiredArgsConstructor
public class SalesWorkflowListener {
    private final WorkflowService workflowService;

    @EventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        String processId = workflowService.startProcess(
                "venta-aprobacion", event.getTenantId(), ...);
        // Actualizar el processInstanceId en el pedido
    }
}
```

Esto desacopla sales de workflow: si workflow no esta presente, el pedido se crea sin proceso de aprobacion.

**Agente responsable**: `zk-developer`.
**Esfuerzo estimado**: 4-6 horas.
**Implementable ahora**: Si, pero requiere cambiar el flujo transaccional.

---

### P14 - MEDIO: EmployeeListVM Accede Directamente a UserRepository

**Archivo**: `/home/lily/eclipse-workspace/lily-lab/platform/ui/zk-app/src/main/java/com/lreyes/platform/ui/zk/vm/EmployeeListVM.java`
**Lineas**: 128-129

```java
UserService uService = SpringUtil.getApplicationContext().getBean(UserService.class);
UserRepository userRepository = SpringUtil.getApplicationContext().getBean(UserRepository.class);
```

**Descripcion Exacta del Problema**:
El ViewModel accede directamente a `UserRepository` (capa de datos) saltandose la capa de servicio. Esto viola la arquitectura en capas y el principio de separacion de responsabilidades. La operacion de "verificar si existe un usuario por username" deberia delegarse a `UserService`, no hacerse directamente contra el repository.

Ademas, ambos beans se obtienen via `SpringUtil.getApplicationContext().getBean()` en lugar de inyeccion, lo cual oculta las dependencias y dificulta el testing.

**Severidad**: MEDIO

**Solucion Propuesta**:

Agregar un metodo `existsByUsername(String)` en `UserService` y usarlo en `EmployeeListVM`:

```java
// En UserService:
public boolean existsByUsername(String username) {
    return userRepository.existsByUsername(username);
}
```

```java
// En EmployeeListVM:
if (!uService.existsByUsername(username)) {
    // crear usuario
}
```

**Agente responsable**: `zk-developer`.
**Esfuerzo estimado**: 30 minutos.
**Implementable ahora**: Si.

---

### P15 - MEDIO: Patron Result Definido Pero No Adoptado (TD-05)

**Archivo**: `/home/lily/eclipse-workspace/lily-lab/platform/shared-libs/domain-core/src/main/java/com/lreyes/platform/shared/domain/Result.java`

**Descripcion Exacta del Problema**:
Se definio una sealed interface `Result<T>` con variantes `Success<T>` y `Failure<T>` (patron Railway/Either), pero ningun servicio de negocio la usa. Todos los servicios lanzan excepciones (`EntityNotFoundException`, `IllegalArgumentException`, `RuntimeException`). Esto es codigo muerto que crea confusion sobre cual es la estrategia de manejo de errores del proyecto.

**Severidad**: MEDIO

**Solucion Propuesta**:

Tomar una decision arquitectonica (nuevo ADR) sobre la estrategia de errores:
- **Opcion A**: Adoptar `Result<T>` en servicios. Requiere refactorizar todos los servicios y sus consumidores.
- **Opcion B**: Eliminar `Result<T>` y estandarizar excepciones. Mas simple para V1.
- **Recomendacion**: Opcion B para V1, con la posibilidad de reintroducir Result en V2 si se adopta programacion funcional.

**Agente responsable**: Decision del arquitecto.
**Esfuerzo estimado**: 1 hora (para Opcion B).
**Implementable ahora**: Si.

---

### P16 - MEDIO: Documentacion del Perfil dev Inconsistente (TD-09)

**Archivos**:
- `/home/lily/eclipse-workspace/lily-lab/platform/CLAUDE.md` - dice "H2 en memoria" para perfil dev
- `/home/lily/eclipse-workspace/lily-lab/platform/platform-app/src/main/resources/application-dev.yml` - configura PostgreSQL real

**Descripcion Exacta del Problema**:
CLAUDE.md indica que el perfil `dev` usa "H2 (en memoria)" pero `application-dev.yml` configura PostgreSQL. Los archivos de migracion H2 existen en `platform-app/src/main/resources/db/migration/h2/` pero el perfil dev no los usa. Esto confunde a nuevos desarrolladores que esperan poder ejecutar `mvn jetty:run -Pdev` sin PostgreSQL.

**Severidad**: MEDIO

**Solucion Propuesta**:

Actualizar CLAUDE.md para reflejar la realidad: el perfil `dev` requiere PostgreSQL via Docker Compose. Opcionalmente, crear un perfil `h2` separado para desarrollo sin base de datos externa.

**Agente responsable**: `zk-developer` (actualizar documentacion).
**Esfuerzo estimado**: 30 minutos.
**Implementable ahora**: Si.

---

### P17 - BAJO: Codigo Duplicado de Color/Branding en LoginVM y LayoutVM

**Archivos**:
- `/home/lily/eclipse-workspace/lily-lab/platform/ui/zk-app/src/main/java/com/lreyes/platform/ui/zk/vm/LoginVM.java` (lineas 100-111: `isLightColor()`)
- `/home/lily/eclipse-workspace/lily-lab/platform/ui/zk-app/src/main/java/com/lreyes/platform/ui/zk/vm/LayoutVM.java` (lineas 165-176: `isLightColor()`, lineas 178-188: `darkenColor()`)

**Descripcion Exacta del Problema**:
El metodo `isLightColor()` esta duplicado textualmente en `LoginVM` y `LayoutVM`. La logica de branding (cargar color primario, calcular color de texto) tambien esta duplicada en `loadBranding()` de ambas clases.

**Severidad**: BAJO

**Solucion Propuesta**:

Extraer a una clase utilitaria `BrandingUtils` en `ui/zk-app/src/main/java/.../model/`:

```java
public final class BrandingUtils {
    public static boolean isLightColor(String hex) { ... }
    public static String darkenColor(String hex, double factor) { ... }
}
```

**Agente responsable**: `zk-ui-designer`.
**Esfuerzo estimado**: 30 minutos.
**Implementable ahora**: Si.

---

### P18 - BAJO: Modulos Stub sin Implementacion (TD-10, TD-11, TD-12)

**Archivos**:
- `/home/lily/eclipse-workspace/lily-lab/platform/core/audit/src/main/java/com/lreyes/platform/core/audit/package-info.java` (solo package-info)
- `/home/lily/eclipse-workspace/lily-lab/platform/core/files/src/main/java/com/lreyes/platform/core/files/package-info.java` (solo package-info)
- `/home/lily/eclipse-workspace/lily-lab/platform/core/form/src/main/java/com/lreyes/platform/core/form/package-info.java` (solo package-info)

**Descripcion Exacta del Problema**:
Tres modulos Maven (`core/audit`, `core/files`, `core/form`) existen como placeholders con solo un `package-info.java`. Las tablas de base de datos para audit (`audit_logs`) y files (`files_meta`) ya existen en las migraciones V1. Esto confunde porque:
- Los modulos aparecen en el build Maven (tiempo de build desperdiciado)
- Otros modulos los declaran como dependencia pero no obtienen funcionalidad
- Las tablas de BD existen pero no se usan

**Severidad**: BAJO

**Solucion Propuesta**:

Opcion A: Implementar `core/audit` con un aspecto AOP que registre operaciones CRUD en `audit_logs`.
Opcion B: Marcar explicitamente los modulos como "planned" en el POM y excluirlos del build por defecto.

**Agente responsable**: Decision del arquitecto.
**Esfuerzo estimado**: 8-16 horas para Opcion A por modulo.
**Implementable ahora**: No. Requiere diserio.

---

### P19 - BAJO: CSRF Deshabilitado en Ambas Cadenas de Seguridad

**Archivo**: `/home/lily/eclipse-workspace/lily-lab/platform/core/auth-security/src/main/java/com/lreyes/platform/core/authsecurity/SecurityConfig.java`
**Lineas**: 64 y 80

```java
.csrf(csrf -> csrf.disable())  // Linea 64: ZK chain
.csrf(csrf -> csrf.disable())  // Linea 80: API chain
```

**Descripcion Exacta del Problema**:
CSRF esta deshabilitado en ambas cadenas. Para la API REST (chain 2), esto es aceptable porque usa JWT bearer tokens (que no son enviados automaticamente por el navegador). Pero para la ZK chain (chain 1) que usa sesiones HTTP, la ausencia de CSRF permite ataques de Cross-Site Request Forgery. Aunque ZK tiene su propia proteccion de desktop ID, la combinacion de `permitAll()` + sin CSRF + sesion HTTP es una superficie de ataque amplia.

**Severidad**: BAJO (mitigado parcialmente por ZK desktop ID).

**Solucion Propuesta**:

Investigar la compatibilidad de CSRF de Spring Security con ZK. ZK envia requests AJAX al path `/zkau` con un desktop ID que funciona como token anti-CSRF implicito. La integracion requiere configurar el `CsrfTokenRepository` para que sea compatible con ZK.

**Agente responsable**: `zk-developer` (investigacion), `zk-bug-solver` (implementacion).
**Esfuerzo estimado**: 4-8 horas.
**Implementable ahora**: Requiere investigacion previa.

---

### P20 - BAJO: DevJwtService Bean Retorna null

**Archivo**: `/home/lily/eclipse-workspace/lily-lab/platform/core/auth-security/src/main/java/com/lreyes/platform/core/authsecurity/SecurityConfig.java`
**Lineas**: 151-158

```java
@Bean
public DevJwtService devJwtService() {
    if (!securityProperties.isJwtLocal()) {
        return null;  // En prod retorna null
    }
    return new DevJwtService(...);
}
```

**Descripcion Exacta del Problema**:
En modo OIDC, este bean retorna `null`. Si algun componente inyecta `DevJwtService` (por ejemplo via `@Autowired` sin `required=false`), obtendra un NPE. Aunque actualmente no parece que ningun componente lo inyecte obligatoriamente, retornar `null` de un `@Bean` method es una practica fragil que puede causar problemas futuros.

**Severidad**: BAJO

**Solucion Propuesta**:

```java
@Bean
@ConditionalOnProperty(name = "app.security.mode", havingValue = "jwt-local")
public DevJwtService devJwtService() {
    return new DevJwtService(
            securityProperties.getJwtSecret(),
            securityProperties.getTokenExpirationMinutes());
}
```

**Agente responsable**: `zk-bug-solver`.
**Esfuerzo estimado**: 15 minutos.
**Implementable ahora**: Si.

---

## Clasificacion: Implementable Ahora vs. Requiere Mas Trabajo

### Implementable Inmediatamente (< 2 horas por item)

| # | Problema | Esfuerzo | Agente |
|---|----------|----------|--------|
| P3 | Statement leak en ConnectionProvider | 30 min | zk-bug-solver |
| P10 | HealthIndicator siempre UP | 5 min | zk-bug-solver |
| P6 | OutboxScheduler usar BD | 2 h | zk-bug-solver |
| P9 | FlowableMultiTenantConfig usar BD | 2 h | zk-bug-solver |
| P1 | Credenciales hardcodeadas | 2 h | zk-bug-solver |
| P12 | JwtDecoder excepcion en OIDC | 1 h | zk-bug-solver |
| P14 | EmployeeListVM acceso a Repository | 30 min | zk-developer |
| P16 | Documentacion perfil dev | 30 min | zk-developer |
| P17 | Codigo duplicado branding | 30 min | zk-ui-designer |
| P20 | DevJwtService retorna null | 15 min | zk-bug-solver |
| P11 | BCryptPasswordEncoder no bean | 1 h | zk-developer |

**Tiempo total estimado**: ~10 horas

### Requiere Mas Disenio/Trabajo (> 4 horas por item)

| # | Problema | Esfuerzo | Requiere |
|---|----------|----------|----------|
| P2 | Autorizacion server-side ZK | 8 h | Diseniar tabla de permisos pagina-rol |
| P5 | Desacoplar ui-zk-app de modules | 16 h | Definir interfaces de servicio en module-api |
| P7 | Tests de integracion | 24 h | Escribir 4-5 test classes con Testcontainers |
| P8 | TenantContext leak en VMs | 6 h | Crear BaseVM, refactorizar todos los VMs |
| P4 | Flowable transaccionalidad | 16 h | Test de integracion + mecanismo de compensacion |
| P13 | Desacoplar Sales de Workflow | 6 h | Cambiar a event listener pattern |

**Tiempo total estimado**: ~76 horas

---

## Recomendaciones por Agente

### Para zk-bug-solver (Fixes de seguridad y bugs)

**Prioridad 1** (hacer primero):
1. P3 - Cerrar Statement objects en `SchemaMultiTenantConnectionProvider`. Fix de 2 lineas con `try-with-resources`.
2. P10 - Cambiar `Health.up()` a `Health.down()` en la rama false de `ModulesHealthIndicator`.
3. P1 - Mover credenciales del seed a variables de entorno; desactivar seed en prod.
4. P6 - Cambiar `OutboxScheduler` para inyectar `TenantRegistryService` y llamar `getActiveTenantNames()`.
5. P9 - Cambiar `FlowableMultiTenantConfig` para inyectar `TenantRegistryService`.

**Prioridad 2**:
6. P12 - Agregar `@ConditionalOnProperty` al bean `jwtDecoder()`.
7. P20 - Agregar `@ConditionalOnProperty` al bean `devJwtService()`.

### Para zk-developer (Refactors y nuevas funcionalidades)

**Prioridad 1**:
1. P2 (Fase 1) - Agregar validacion de roles en cada ViewModel (`requireRole()` al inicio de `@Init`).
2. P8 - Crear un `BaseVM` con metodos `withTenant()` y `withTenantVoid()` que establezcan y limpien TenantContext.
3. P14 - Mover el acceso a `UserRepository` en `EmployeeListVM` a traves de `UserService`.

**Prioridad 2**:
4. P7 - Escribir los tests de integracion con Testcontainers (`PlatformBootstrapIT`, `MultiTenantIsolationIT`, `SalesWorkflowIT`, `SecurityIT`).
5. P11 - Declarar `PasswordEncoder` como bean Spring e inyectarlo en `UserService` y `PlatformUserService`.
6. P5 (Opcion B) - Agregar test de ArchUnit para limitar dependencias de `ui.zk` hacia `modules.*`.
7. P16 - Actualizar CLAUDE.md con la informacion correcta del perfil dev.

### Para zk-ui-designer (UI y presentacion)

**Prioridad 1**:
1. P2 (Fase 2) - Disenar como mostrar mensajes de error cuando un usuario no tiene permiso para acceder a una pagina (en lugar de simplemente no mostrar el menu).
2. P17 - Extraer `BrandingUtils` con los metodos de calculo de color compartidos entre `LoginVM` y `LayoutVM`.

**Prioridad 2**:
3. Verificar que el patron de navegacion de `LayoutVM` (include dinamic) sea compatible con la adicion de validacion de roles por pagina.

---

## Analisis de Riesgos Detallado

### Riesgos del Baseline - Reevaluacion

| Riesgo del Baseline | Mi Evaluacion | Severidad Ajustada | Comentario |
|---------------------|---------------|---------------------|------------|
| Tenant data leakage via ThreadLocal leak | **Subestimado** | ALTO | Los VMs de ZK no limpian TenantContext (P8). En teoria el TenantFilter limpia para REST, pero para ZK no hay filtro que limpie. |
| SQL injection en SET search_path | Correcto | MUY BAJO | La validacion regex es solida y se aplica en TenantFilter. |
| Hardcoded admin credentials in production | **Subestimado** | CRITICO | No solo es un riesgo en prod -- el log en texto plano expone las credenciales en cualquier entorno con logging centralizado. |
| Flowable shared schema limits isolation | Correcto | MEDIO | El uso de tenantId nativo de Flowable es adecuado para V1. |
| Schema count scaling limit (~500) | Correcto | BAJO para V1 | Es prematuro preocuparse por esto en V1. |
| ZK UI session hijacking | **Subestimado** | ALTO | No solo session hijacking: la ausencia de autorizacion server-side (P2) es un problema mas fundamental que CSRF. |
| Outbox scheduler missing new tenants | Correcto | MEDIO | Confirmado en el codigo (P6). |
| No test coverage | **Subestimado** | ALTO | Hay tests unitarios (21 archivos), pero cero tests de integracion end-to-end que validen el sistema como un todo. |

### Riesgos Nuevos Identificados

#### ALTO: Inconsistencia Transaccional entre JPA y Flowable

**Probabilidad**: Media
**Impacto**: Alto
**Escenario**: `SalesService.create()` guarda un SalesOrder (JPA, schema del tenant) y luego llama a `workflowService.startProcess()` (Flowable, schema platform, DataSource separado). Si el `startProcess` tiene exito pero el segundo `save()` (linea 78) falla, el pedido queda sin `processInstanceId` pero el proceso Flowable ya esta activo. El proceso referenciara un orderId que no tiene workflow asociado en el lado del negocio.
**Mitigacion recomendada**: Documentar la decision de no-transaccionalidad como ADR. Implementar un healthcheck que detecte procesos Flowable huerfanos.

#### MEDIO: Escalado del Scheduler Outbox en Multi-Tenant

**Probabilidad**: Media (cuando haya >50 tenants)
**Impacto**: Medio
**Escenario**: `OutboxScheduler` itera secuencialmente por cada tenant con un `fixedDelay` de 10 segundos. Con 100 tenants, cada iteracion procesaria 100 x 50 = 5000 eventos en el peor caso, todo en un solo hilo. Si el procesamiento de un tenant falla o es lento, retrasa a todos los demas.
**Mitigacion recomendada**: Considerar paralelizar el procesamiento por tenant usando un `ExecutorService` con pool de hilos limitado.

#### MEDIO: Falta de Rate Limiting en API REST

**Probabilidad**: Media
**Impacto**: Medio-Alto
**Escenario**: Los endpoints REST (`/api/customers`, `/api/sales`, etc.) no tienen rate limiting. Un usuario malintencionado o un bug en un cliente puede generar miles de requests por segundo, sobrecargando la base de datos y afectando a todos los tenants.
**Mitigacion recomendada**: Agregar rate limiting por tenant a nivel de gateway o con un filtro Spring (por ejemplo, con Bucket4j o Spring Cloud Gateway).

---

## Veredicto Final

### Evaluacion General

**Puntuacion**: 6/10

**Resumen en un Parrafo**:
La arquitectura de Platform demuestra decisiones fundamentales bien pensadas: el modular monolith es apropiado para el tamano del equipo, el aislamiento por schema PostgreSQL es una solucion solida para multi-tenancy, y el patron outbox con SPI para eventos externos muestra vision de futuro. Sin embargo, el sistema tiene 4 problemas criticos (credenciales hardcodeadas, sin autorizacion server-side en ZK, leak de Statement JDBC, y riesgo transaccional con Flowable) que lo hacen inaceptable para produccion en su estado actual. Los 6 problemas de severidad alta (especialmente la ausencia de tests de integracion y el TenantContext leak) reducen la confianza en que el sistema se comportara correctamente bajo carga real. La buena noticia es que la mayoria de estos problemas son corregibles sin cambios arquitectonicos fundamentales -- son bugs de implementacion, no fallas de disenio.

### Recomendacion a Stakeholders

- [ ] **Aprobar y Proceder**: Arquitectura solida, refinamientos menores durante implementacion
- [x] **Aprobar con Condiciones**: Arquitectura viable pero debe resolver los problemas criticos primero
- [ ] **Solicitar Revision Mayor**: Arquitectura tiene fallas fundamentales que requieren retrabajo
- [ ] **Rechazar y Reiniciar**: Arquitectura inadecuada para el problema

**Mi Recomendacion**: Aprobar con Condiciones

**Razonamiento**: Las decisiones arquitectonicas de base son correctas. El modular monolith con Maven multi-module, schema-per-tenant, outbox pattern, y Flowable integration demuestran un buen dominio de patrones enterprise. Los problemas encontrados son de implementacion (leaks, falta de validaciones, configuracion fragil), no de disenio fundamental. Todos los problemas criticos y altos se pueden resolver en 2-3 sprints sin cambiar la arquitectura. La condicion es: NO desplegar a un entorno accesible por usuarios reales hasta que P1, P2, P3, P4, P6, P7, P8, P9, y P10 esten resueltos y validados con tests de integracion.

---

**Revision Completada**: 2026-03-06

**Nota al Arquitecto**: Esta revision es constructiva, no destructiva. El objetivo es identificar problemas antes de que se conviertan en errores costosos en produccion. La base arquitectonica es solida -- los problemas encontrados son oportunidades de mejora, no razones para empezar de cero.
