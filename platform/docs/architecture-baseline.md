# Architectural Baseline: Platform

**Status**: Baseline Established
**Last Updated**: 2026-03-06
**Version**: 0.1.0-SNAPSHOT
**Architect**: Software Architect Agent (analysis of existing codebase)

---

## 1. Problem Statement

### 1.1 Business Context

The platform is a **multi-tenant SaaS foundation** designed to serve multiple organizations (tenants) from a single deployment. It provides core infrastructure (authentication, authorization, multi-tenancy, workflows, auditing) plus pluggable business modules (customers, employees, sales, documents).

The key business problem: deliver a V1 platform rapidly with strong tenant isolation, integrated BPMN workflows, and a rich server-side UI, while maintaining the ability to evolve toward microservices later.

### 1.2 Current State

The project is at **version 0.1.0-SNAPSHOT** -- an early-stage modular monolith with:
- 5 top-level Maven module groups (shared-libs, core, modules, ui, platform-app)
- 20 Maven sub-modules total
- Working multi-tenant schema isolation
- Functional ZK MVVM UI with login, CRUD views, and workflow task management
- Flowable BPMN integration with a sales approval process
- Transactional outbox pattern for domain events (with NoOp external publisher)
- Dual authentication: JWT-local (dev/local) and OIDC/Keycloak (prod)

### 1.3 Stakeholders

- **Development Team**: Small team (~1-3 developers based on project structure)
- **Operations**: Docker Compose-based infrastructure, not yet Kubernetes
- **End Users**: Tenant administrators and operators accessing via ZK UI; external integrations via REST API

---

## 2. Technology Stack

### 2.1 Stack Summary

```
Language:        Java 17 (maven.compiler.source/target)
Framework:       Spring Boot 3.3.5
UI Framework:    ZK 10.0.0-jakarta (MVVM pattern)
Workflow Engine: Flowable 7.0.1 (spring-boot-starter-process)
Database:        PostgreSQL 16 (multi-tenant by schema)
Migrations:      Flyway 10.10.0 (programmatic, per-tenant)
ORM:             Hibernate (JPA) with schema-based multi-tenancy
Mapping:         MapStruct 1.5.5.Final
Code Generation: Lombok 1.18.30
API Docs:        SpringDoc OpenAPI 2.3.0
Auth (prod):     Keycloak 23 (OIDC/JWT)
Auth (dev):      Custom HS256 JWT (DevJwtService)
Storage:         MinIO (S3-compatible) -- placeholder, not fully implemented
Testing:         Testcontainers 1.19.3, JUnit 5
Build:           Maven 3.8+ with multi-module reactor
Container:       Docker multi-stage build (Eclipse Temurin 17)
```

### 2.2 Version Matrix

| Component                  | Version           | Source                     |
|----------------------------|-------------------|----------------------------|
| Java                       | 17                | pom.xml `java.version`     |
| Spring Boot                | 3.3.5             | pom.xml `spring-boot.version` |
| ZK                         | 10.0.0-jakarta    | pom.xml `zk.version`       |
| ZK Spring Boot Integration | 3.2.7.1           | pom.xml `zkspringboot.version` |
| Flowable                   | 7.0.1             | pom.xml `flowable.version` |
| Flyway                     | 10.10.0           | pom.xml `flyway.version`   |
| MapStruct                  | 1.5.5.Final       | pom.xml `mapstruct.version`|
| Lombok                     | 1.18.30           | pom.xml `lombok.version`   |
| SpringDoc OpenAPI          | 2.3.0             | pom.xml `springdoc.version`|
| Testcontainers             | 1.19.3            | pom.xml `testcontainers.version` |
| PostgreSQL (Docker)        | 16-alpine         | docker-compose.yml         |
| Keycloak (Docker)          | 23.0              | docker-compose.yml         |

---

## 3. Architecture Overview

### 3.1 Architectural Style

**Modular Monolith** (Microkernel pattern) deployed as a single Spring Boot JAR.

**Rationale** (from ADR-0002):
- Speed of development for V1 -- single deploy, simple debugging, no network latency between modules
- Each module is a separate Maven JAR with explicit dependencies
- Designed for future extraction to independent services (Ports & Adapters style)
- Minimal operational overhead -- no service discovery, circuit breakers, or container orchestration needed for V1

### 3.2 Module Dependency Graph

```
platform-app (Spring Boot runnable -- assembles everything)
 |
 +-- shared-libs/
 |    +-- domain-core        (BaseEntity, DomainEvent, Result, DomainException)
 |    +-- common-dto         (ApiResponse, PageResponse, ErrorResponse)
 |    +-- mapping            (BaseMapper, DefaultMapStructConfig)
 |    +-- utils              (JsonUtils, SlugUtils)
 |
 +-- core/
 |    +-- tenancy            (depends: shared-domain-core)
 |    |    Multi-tenant resolver, connection provider, Flyway per tenant,
 |    |    User/Role entities (JPA), platform admin JDBC entities
 |    |
 |    +-- auth-security      (depends: shared-domain-core, core-tenancy)
 |    |    Spring Security config, JWT, OIDC, RBAC
 |    |
 |    +-- events             (depends: shared-domain-core, core-tenancy)
 |    |    DomainEventPublisher, Outbox pattern, ExternalEventPublisher SPI
 |    |
 |    +-- workflow            (depends: none internal, Flowable starter)
 |    |    FlowableDatasourceConfig, WorkflowService, WorkflowController
 |    |
 |    +-- catalogs           (depends: shared-domain-core)
 |    +-- audit              (stub -- package-info only)
 |    +-- files              (stub -- package-info only)
 |    +-- form               (stub -- package-info only)
 |
 +-- modules/
 |    +-- customers          (depends: shared-*, core-tenancy, core-events, core-audit)
 |    +-- employees          (depends: shared-*, core-tenancy, core-events, core-audit)
 |    +-- documents          (depends: shared-*, core-tenancy, core-events, core-audit)
 |    +-- sales              (depends: shared-*, core-tenancy, core-events, core-audit, core-workflow)
 |
 +-- ui/
      +-- zk-app             (depends: core-auth-security, core-tenancy, core-workflow,
                               core-catalogs, mod-customers, mod-employees)
```

### 3.3 High-Level Component Diagram

```
 +-----------------------------------------------------------------------+
 |                     platform-app (Fat JAR)                            |
 |                                                                       |
 |  +---------------------+    +--------------------+                    |
 |  |  REST API Layer      |    |   ZK UI Layer      |                   |
 |  |  (Spring MVC)        |    |   (MVVM ViewModels)|                   |
 |  |  /api/**             |    |   /zul/**           |                   |
 |  +----------+-----------+    +----------+---------+                   |
 |             |                           |                             |
 |  +----------v---------------------------v---------+                   |
 |  |           Cross-Cutting Concerns                |                  |
 |  |  RequestIdFilter | TenantFilter | SecurityConfig|                  |
 |  |  JwtTenantFilter | GlobalExceptionHandler       |                  |
 |  +-------------------------------------------------+                  |
 |             |                                                         |
 |  +----------v-----------+  +------------------+  +----------------+   |
 |  |    Core Services     |  | Business Modules |  |   Shared Libs  |   |
 |  |  tenancy, auth,      |  | customers,       |  | domain-core,   |   |
 |  |  events, workflow,   |  | employees, sales,|  | common-dto,    |   |
 |  |  catalogs, audit     |  | documents        |  | mapping, utils |   |
 |  +----------+-----------+  +--------+---------+  +----------------+   |
 |             |                       |                                 |
 |  +----------v-----------------------v---------+                       |
 |  |          Data Access (Hibernate JPA)        |                      |
 |  |  SchemaMultiTenantConnectionProvider        |                      |
 |  |  TenantIdentifierResolver                   |                      |
 |  +---------------------------------------------+                     |
 +-----------------------------------------------------------------------+
              |                         |
   +----------v----------+   +----------v-----------+
   |   PostgreSQL 16      |   |     Keycloak 23      |
   |                      |   |     (OIDC, prod)     |
   |  schema: platform    |   +----------------------+
   |    - tenants         |
   |    - platform_users  |   +----------------------+
   |    - tenant_schemas  |   |     MinIO             |
   |    - act_* (Flowable)|   |     (S3, placeholder) |
   |                      |   +----------------------+
   |  schema: acme        |
   |    - users, roles    |
   |    - customers       |
   |    - employees       |
   |    - sales_orders    |
   |    - outbox_events   |
   |    - audit_logs      |
   |    - catalogs        |
   |                      |
   |  schema: globex      |
   |    (same structure)  |
   +----------------------+
```

---

## 4. Multi-Tenancy Architecture (ADR-0001)

### 4.1 Strategy

**Schema-per-tenant** using Hibernate's `MultiTenantConnectionProvider` and `CurrentTenantIdentifierResolver`.

### 4.2 Tenant Resolution Flow

```
HTTP Request
    |
    v
[RequestIdFilter]  -- generates/propagates X-Request-Id, sets MDC
    |
    v
[TenantFilter]     -- extracts tenant from X-Tenant-Id header
    |                  validates format (regex: ^[a-z][a-z0-9_]{1,62}$)
    |                  validates registration (BD cache via TenantRegistryService)
    |                  sets TenantContext.setCurrentTenant()
    v
[Spring Security]  -- validates JWT / OIDC token
    |
    v
[JwtTenantFilter]  -- if TenantContext is empty, extracts tenant_id from JWT claim
    |
    v
[Controller]  -->  [Service]  -->  [Repository]
                                       |
                                  [Hibernate]
                               SchemaMultiTenantConnectionProvider
                               SET search_path TO <tenant>
                                       |
                                       v
                                  PostgreSQL (tenant schema)
```

### 4.3 Schema Layout

| Schema     | Contents                                                              | Managed By               |
|------------|-----------------------------------------------------------------------|--------------------------|
| `platform` | `tenants`, `tenant_schemas`, `platform_users`, Flowable `act_*` tables | PlatformMigrationService (Flyway: `db/migration/platform/`) |
| `<tenant>` | `users`, `roles`, `user_roles`, `audit_logs`, `catalogs`, `files_meta`, `outbox_events`, `customers`, `employees`, `sales_orders`, `order_lines`, `documents`, `role_schemas` | TenantMigrationService (Flyway: `db/migration/base/` + `db/migration/modules/`) |
| `public`   | Not used                                                              | --                       |

### 4.4 Bootstrap Sequence (TenantBootstrapRunner)

1. Migrate schema `platform` (PlatformMigrationService -- Flyway on `db/migration/platform/`)
2. Seed default platform admin (`platformadmin / admin123`)
3. Seed tenants from YAML config to BD (TenantRegistryService.seedFromYaml)
4. Read active tenants from BD
5. For each active tenant: CREATE SCHEMA IF NOT EXISTS + Flyway migrate (`db/migration/base/` + `db/migration/modules/`)

### 4.5 Flyway Migration Versioning

| Version | Location                    | Content                           |
|---------|-----------------------------|-----------------------------------|
| V1      | `db/migration/base/`        | Core schema (users, roles, audit_logs, catalogs, files_meta) |
| V2      | `db/migration/base/`        | Events outbox table               |
| V3      | `db/migration/modules/`     | Customers table                   |
| V4      | `db/migration/modules/`     | Employees table                   |
| V5      | `db/migration/modules/`     | Sales orders + order lines        |
| V6      | `db/migration/modules/`     | Documents table                   |
| V7      | `db/migration/base/`        | Audit columns additions           |
| V8      | `db/migration/base/`        | Role schemas table                |

Platform schema:

| Version | Location                       | Content                          |
|---------|--------------------------------|----------------------------------|
| V1      | `db/migration/platform/`       | tenants, tenant_schemas, platform_users |
| V2      | `db/migration/platform/`       | Tenant branding (primary_color, logo_path) |

H2 variants (for dev profile without PostgreSQL): `db/migration/h2/V1-V8` mirror the above.

---

## 5. Security Architecture

### 5.1 Authentication Modes

| Mode       | Profile    | Mechanism                                  | Token Source        |
|------------|------------|--------------------------------------------|---------------------|
| `jwt-local`| dev, local | HS256 symmetric JWT via `DevJwtService`     | `POST /api/auth/login` |
| `oidc`     | prod       | Keycloak OIDC / RS256 JWT                   | Keycloak token endpoint |

### 5.2 Security Filter Chains

**Chain 1 (Order=1): ZK UI**
- Matches: `/zul/**`, `/zkau/**`, `/login`, `/`
- Session-based (IF_REQUIRED)
- CSRF disabled
- All requests permitted (authentication handled by LoginVM via session attributes)

**Chain 2 (Order=2): REST API**
- Matches: everything else
- JWT bearer token (OAuth2 Resource Server)
- Public endpoints: `/api/auth/**`, `/api/logos/**`, `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/h2-console/**`
- Platform admin: `/api/platform/**` requires ROLE_platform_admin
- Tenant admin: `/api/admin/**` requires ROLE_admin
- All other: authenticated

### 5.3 RBAC Model

| Role             | Scope    | Capabilities                              |
|------------------|----------|-------------------------------------------|
| `platform_admin` | Global   | Manage tenants, schemas, platform users; can switch tenants in UI |
| `admin`          | Tenant   | Manage users, roles, catalogs, permissions within tenant |
| `gestor`         | Tenant   | Operations management, review workflow tasks |
| `operador`       | Tenant   | Standard operations                        |
| `auditor`        | Tenant   | Read-only access                           |

Roles are seeded per tenant via `V1__core_schema.sql`. Permission mapping to schema_types is done via the `role_schemas` table (V8).

### 5.4 ZK UI Session Authentication

The ZK UI uses a custom session-based auth model:
1. `LoginVM` authenticates against `UserService` (tenant user) or `PlatformUserService` (platform admin)
2. On success, stores `UiUser` object in HTTP session
3. `LayoutVM.init()` reads session; redirects to login if absent
4. Tenant context is set via `TenantContext.setCurrentTenant()` in each VM
5. **NOTE**: ZK UI does NOT use JWT tokens -- it bypasses the OAuth2 security chain entirely via permitAll

---

## 6. Domain Events & Outbox Pattern

### 6.1 Event Flow

```
Service.create()
    |
    +-- DomainEventPublisher.publish(event, aggregateType, aggregateId)
         |
         +-- (1) Spring ApplicationEventPublisher.publishEvent(event)  [synchronous, local]
         |
         +-- (2) OutboxService.save(event)  [persisted in same DB transaction]
                    |
                    v
              outbox_events table (status=PENDING, payload=JSON)
```

### 6.2 Outbox Processing

```
OutboxScheduler (@Scheduled, fixedDelay=10s)
    |
    +-- For each tenant:
         TenantContext.setCurrentTenant(tenant)
         OutboxService.processPending(batchSize=50)
              |
              +-- Read PENDING events
              +-- ExternalEventPublisher.publish(topic, key, payload)
              +-- Mark PUBLISHED or FAILED (max 5 retries)
```

### 6.3 External Event Publisher SPI

```java
public interface ExternalEventPublisher {
    void publish(String topic, String key, String payload);
}
```

- **V1 (current)**: `NoOpExternalEventPublisher` -- logs only, annotated `@ConditionalOnMissingBean`
- **V2 (planned)**: Kafka implementation that auto-replaces NoOp when present

### 6.4 Domain Event Base Class

```java
public abstract class DomainEvent {
    UUID eventId;       // auto-generated
    Instant occurredAt; // auto-generated
    String tenantId;    // passed by caller
    abstract String eventType(); // e.g., "customer.created"
}
```

Concrete events: `CustomerCreatedEvent`, `EmployeeCreatedEvent`, `OrderCreatedEvent`, `DocumentCreatedEvent`.

---

## 7. Flowable BPMN Integration (ADR-0003)

### 7.1 Architecture

- Flowable runs with a **separate DataSource** pointing to schema `platform` (FlowableDatasourceConfig)
- Flowable tables (`act_*`) are shared across all tenants
- Tenant isolation in Flowable is achieved via Flowable's native `tenantId` field on process instances and tasks

### 7.2 Multi-Tenant Deployment

`FlowableMultiTenantConfig` deploys BPMN process definitions per tenant at startup:
1. Reads `app.tenants` from config
2. Scans `classpath*:processes/*.bpmn20.xml`
3. For each tenant + each BPMN file: deploys with `tenantId(<tenant>)`
4. Skips if already deployed (checks by deployment name)

### 7.3 Workflow Service API

| Method                | Description                                       |
|-----------------------|---------------------------------------------------|
| `startProcess`        | Start a process instance with tenantId, businessKey, variables |
| `getTasksByAssignee`  | List tasks assigned to a user, filtered by tenantId |
| `getTasksByCandidateGroup` | List unassigned tasks by candidate group + tenantId |
| `getAllPendingTasks`   | All pending tasks for a tenant                    |
| `claimTask`           | Assign a task to a user                           |
| `completeTask`        | Complete a task with output variables              |
| `getProcessStatus`    | Get active or historic process instance status    |

### 7.4 Process Definition: venta-aprobacion

```
Start --> [Revisar Venta (gestor)] --> <monto > 10000?> --yes--> [Aprobacion Gerencia (admin)] --> End
                                              |
                                              no
                                              |
                                              v
                                         End (Auto-aprobado)
```

Variables: orderId, cliente, vendedor, monto, descripcion, aprobado, comentario.

### 7.5 Sales Module Integration

`SalesService.create()`:
1. Creates SalesOrder with PENDING_APPROVAL status
2. Starts Flowable process `venta-aprobacion` with order variables
3. Stores `processInstanceId` on the SalesOrder entity
4. Publishes `OrderCreatedEvent` via domain events

---

## 8. Module Structure & Contracts

### 8.1 Business Module Template

Each business module follows this structure:

```
modules/<name>/
  pom.xml                      (depends: shared-*, core-tenancy, core-events, core-audit)
  src/main/java/.../
    <Entity>.java              (extends BaseEntity, JPA @Entity)
    <Entity>Repository.java    (Spring Data JpaRepository)
    <Entity>Service.java       (@Service, @Transactional)
    <Entity>Controller.java    (@RestController, /api/<name>)
    <Entity>Mapper.java        (MapStruct mapper)
    dto/
      Create<Entity>Request.java
      Update<Entity>Request.java
      <Entity>Response.java
    event/
      <Entity>CreatedEvent.java (extends DomainEvent)
    package-info.java
  src/main/resources/
    db/migration/modules/V<N>__<name>.sql
```

### 8.2 Inter-Module Dependencies

| Module     | Depends on Core          | Depends on Other Modules | Notes                          |
|------------|--------------------------|--------------------------|--------------------------------|
| customers  | tenancy, events, audit   | none                     | Pure CRUD + events             |
| employees  | tenancy, events, audit   | none                     | Pure CRUD + events             |
| documents  | tenancy, events, audit   | none                     | Pure CRUD + events             |
| sales      | tenancy, events, audit, **workflow** | none          | Flowable integration for approval |
| ui/zk-app  | auth-security, tenancy, workflow, catalogs | **mod-customers, mod-employees** | Direct service access |

### 8.3 Key Contracts

**BaseEntity** (shared-libs/domain-core):
- UUID primary key (auto-generated)
- Auditing: createdAt, updatedAt, createdBy, updatedBy (via JPA @EntityListeners)
- Optimistic locking: @Version field
- Equals/hashCode based on ID (null-safe for new entities)

**Result<T>** (shared-libs/domain-core):
- Sealed interface: Success<T> | Failure<T>
- Functional fold/map operations
- Currently NOT used by services (they use exceptions instead -- see Technical Debt)

**DomainEvent** (shared-libs/domain-core):
- Abstract class with eventId, occurredAt, tenantId
- Concrete events extend this and implement `eventType()`

**BaseMapper<E, D>** (shared-libs/mapping):
- Generic interface for entity-to-DTO mapping
- Currently NOT used by module mappers (they define custom methods -- see Technical Debt)

**ErrorResponse** (shared-libs/common-dto):
- RFC 9457 ProblemDetails compatible
- Used consistently by GlobalExceptionHandler

---

## 9. ZK UI Architecture

### 9.1 Layer Structure

```
ui/zk-app/
  src/main/java/.../
    vm/          ViewModels (MVVM pattern)
      LoginVM         Session-based login (tenant + platform)
      LayoutVM        Shell: sidebar menu, tenant switcher, navigation
      DashboardVM     Summary dashboard
      CustomerListVM  CRUD for customers
      EmployeeListVM  CRUD for employees
      UserListVM      Tenant user management
      RoleListVM      Tenant role management
      CatalogListVM   Catalog management
      WorkflowTasksVM Flowable task list and completion
      TenantListVM    Platform admin: tenant CRUD
      TenantSchemaListVM  Platform admin: schema management
      PlatformUserListVM  Platform admin: platform user CRUD
      RoleSchemaVM    Admin: role-to-schema permission mapping
      AssistantVM     AI assistant panel
    model/       UI-specific POJOs
      UiUser          Session user (username, tenantId, roles, isPlatformAdmin)
      MenuItem        Menu item (id, label, page, requiredRole)
      SchemaMenuRegistry  Static registry: schema_type -> menu items
      CatalogItem, CustomerItem, EmployeeItem, UserItem, RoleItem, RoleCheckItem
    service/
      AssistantEngine AI assistant backend
  src/main/resources/
    web/zul/     ZUL view files
      login.zul
      index.zul          (shell layout with <include> for content area)
      dashboard.zul
      customers/list.zul
      employees/list.zul
      admin/users.zul, roles.zul, catalogs.zul, role-schemas.zul
      platform/tenants.zul, schemas.zul, platform-users.zul
      workflow/tasks.zul
      assistant-panel.zul
```

### 9.2 Navigation Model

- `LayoutVM` controls a central `<include>` component
- Menu items map to ZUL page paths
- `SchemaMenuRegistry` filters menu items based on tenant's active schema_types and user's role permissions
- Platform admins see all menus; regular users see only menus for their role's assigned schema_types

### 9.3 Tenant Context in ZK

- LoginVM sets `TenantContext` for authentication queries and clears after
- LayoutVM sets `TenantContext` on init for the session duration
- Platform admins can switch tenants via `changeTenant()` command, which triggers page reload

### 9.4 ZUL Serving in JAR Mode

`ZulForwardController` registers a servlet filter that forwards `/zul/*` requests to `/zkau/web/zul/*` because ZK's DHtmlLayoutServlet cannot resolve ZUL files from the classpath in JAR packaging.

---

## 10. Profiles & Configuration

### 10.1 Profile Matrix

| Profile | Database  | Auth Mode  | Flyway            | Flowable DDL | ZK UI      |
|---------|-----------|------------|-------------------|--------------|------------|
| `dev`   | PostgreSQL| jwt-local  | Programmatic      | auto-update  | Enabled    |
| `local` | PostgreSQL| jwt-local  | Programmatic      | auto-update  | Enabled    |
| `prod`  | PostgreSQL| OIDC       | Programmatic      | disabled     | Enabled    |

Note: The `dev` profile description in `CLAUDE.md` says "H2 en memoria" but `application-dev.yml` actually configures PostgreSQL. H2 support exists via separate migration files but is not the default dev profile behavior.

### 10.2 DataSource Configuration

Three DataSources per profile:

| DataSource              | Spring Property               | Schema     | Pool        | Purpose                    |
|-------------------------|-------------------------------|------------|-------------|----------------------------|
| Primary (auto-config)   | `spring.datasource.*`         | dynamic    | HikariCP    | JPA entities (tenant schemas) |
| Platform                | `spring.datasource-platform.*`| `platform` | 5 connections | Platform admin JDBC queries |
| Flowable                | `spring.datasource-flowable.*`| `platform` | 5 connections | Flowable engine tables     |

---

## 11. Existing Architecture Decision Records

### ADR-0001: Multi-tenant by schema in PostgreSQL
- **Status**: Accepted
- **Decision**: Schema-per-tenant using `hibernate.multiTenancy=SCHEMA`
- **Key consequences**: Flyway per tenant, resolver from JWT/header, Flowable in shared `platform` schema, practical limit ~200-500 schemas

### ADR-0002: Modular monolith as initial architecture
- **Status**: Accepted
- **Decision**: Maven multi-module monolith with Ports & Adapters readiness
- **Key consequences**: Modules must not access internals of other modules; extraction requires creating separate Spring Boot app, replacing internal calls with REST/gRPC/messaging

### ADR-0003: Flowable as BPMN engine for V1
- **Status**: Accepted
- **Decision**: Flowable 7 with Spring Boot Starter
- **Key consequences**: Separate datasource for schema `platform`, auto-deploy of BPMN files, user tasks assigned by role via Spring Security

---

## 12. Architectural Patterns Used

### 12.1 Pattern Inventory

| Pattern                     | Location                    | Maturity    |
|-----------------------------|-----------------------------|-------------|
| Multi-tenancy by Schema     | core/tenancy                | Implemented |
| Transactional Outbox        | core/events                 | Implemented (NoOp external publisher) |
| MVVM (Model-View-ViewModel) | ui/zk-app                   | Implemented |
| Domain Events               | shared-libs/domain-core + core/events | Implemented |
| Repository Pattern          | All modules (Spring Data JPA) | Implemented |
| DTO Mapping                 | MapStruct in all modules    | Implemented |
| Result Type (Railway)       | shared-libs/domain-core     | Defined, NOT adopted in services |
| Global Exception Handling   | platform-app config         | Implemented |
| Request Correlation         | RequestIdFilter + MDC       | Implemented |
| Optimistic Locking          | BaseEntity @Version         | Implemented |
| JPA Auditing                | BaseEntity @CreatedDate etc | Implemented |
| Dual Security Chains        | SecurityConfig              | Implemented |
| SPI for External Events     | ExternalEventPublisher      | Interface defined, NoOp implementation |
| Schema-based Menu Filtering | SchemaMenuRegistry + RoleSchemaService | Implemented |
| BPMN Workflow               | core/workflow + Flowable    | Implemented (1 process) |

---

## 13. Data Flow Diagrams

### 13.1 Create Customer Flow

```
[REST Client]
    |  POST /api/customers  +  X-Tenant-Id: acme  +  Bearer: <JWT>
    v
[RequestIdFilter] -> MDC(requestId)
    v
[TenantFilter] -> TenantContext.set("acme") -> validates registered
    v
[SecurityFilterChain] -> validates JWT
    v
[JwtTenantFilter] -> (no-op, already set)
    v
[CustomerController.create(request)]
    v
[CustomerService.create()]
    |  1. CustomerMapper.toEntity(request)
    |  2. customerRepository.save(customer)     -- Hibernate: SET search_path TO acme
    |  3. DomainEventPublisher.publish(CustomerCreatedEvent)
    |     3a. Spring ApplicationEventPublisher.publishEvent() [sync]
    |     3b. OutboxService.save() -> INSERT outbox_events [same TX]
    v
[CustomerController] -> ResponseEntity 201 Created
```

### 13.2 Create Sales Order Flow (with Flowable)

```
[SalesController.create()]
    v
[SalesService.create()]
    |  1. SalesOrderMapper.toEntity(request)
    |  2. Calculate order line totals
    |  3. salesOrderRepository.save(order)
    |  4. workflowService.startProcess("venta-aprobacion", tenantId, orderNumber, variables)
    |     -> Flowable RuntimeService creates process instance in platform schema
    |  5. order.setProcessInstanceId(processId)
    |  6. salesOrderRepository.save(order)  [update with processId]
    |  7. DomainEventPublisher.publish(OrderCreatedEvent)
    v
[Response: OrderResponse with processInstanceId]
```

---

## 14. Extension Points & Contracts Between Modules

### 14.1 Extension Points

| Extension Point           | Interface                    | Current Implementation | How to Extend |
|---------------------------|------------------------------|------------------------|---------------|
| External Event Publishing | `ExternalEventPublisher`     | `NoOpExternalEventPublisher` | Add Kafka bean, NoOp auto-disables via `@ConditionalOnMissingBean` |
| Menu Registration         | `SchemaMenuRegistry`         | Static map              | Add entries to SCHEMA_MENU_MAP for new schema_types |
| BPMN Processes            | `classpath*:processes/*.bpmn20.xml` | `venta-aprobacion.bpmn20.xml` | Drop new BPMN files in classpath |
| Flyway Migrations (base)  | `classpath:db/migration/base/` | V1-V8                | Add V9+ SQL files |
| Flyway Migrations (modules)| `classpath:db/migration/modules/` | V3-V6            | Add new version SQL files |
| JPA Auditor               | `AuditorAware<String>`       | SecurityContext reader  | Replace bean for custom auditor |
| Health Indicators         | `HealthIndicator`            | `ModulesHealthIndicator`| Add more indicator beans |

### 14.2 Module Communication

**Current**: Direct Java method calls within the same Spring context (monolith).

**No inter-module service dependencies exist between business modules** -- modules only depend on core services. The only cross-module dependency is in the UI layer (ui-zk-app depends on mod-customers and mod-employees for CRUD ViewModels).

**Future (extraction)**: Replace direct calls with REST/gRPC/messaging. The DomainEvent + outbox pattern is already positioned for this.

---

## 15. Technical Debt & Inconsistencies

### 15.1 Critical Issues

| # | Issue | Location | Impact |
|---|-------|----------|--------|
| TD-01 | **Hardcoded platform admin credentials** `platformadmin/admin123` seeded on every startup | `PlatformUserService.seedDefaultAdmin()` | Security risk in production; credentials logged to stdout |
| TD-02 | **ZK UI bypasses Spring Security entirely** -- permitAll for all /zul/** requests | `SecurityConfig.zkFilterChain()` | No server-side authorization enforcement for ZK UI; relies solely on session-based UiUser object |
| TD-03 | **TenantContext uses ThreadLocal, incompatible with reactive/virtual threads** | `TenantContext.java` | Will break if moving to Spring WebFlux or Java 21 virtual threads without scoped values |
| TD-04 | **Statement objects not closed** in SchemaMultiTenantConnectionProvider.setSchema() / resetSchema() | `SchemaMultiTenantConnectionProvider.java` lines 72, 88 | Resource leak on every JPA operation; `connection.createStatement().execute(sql)` creates unclosed Statement |

### 15.2 Design Inconsistencies

| # | Issue | Location | Impact |
|---|-------|----------|--------|
| TD-05 | **Result type defined but never used** -- services throw exceptions instead of returning Result | `Result.java` vs all *Service.java | Dead code; inconsistent error handling strategy |
| TD-06 | **BaseMapper not used by module mappers** -- CustomerMapper/SalesOrderMapper define custom methods rather than extending BaseMapper | Mapping layer | Unused abstraction; mappers are not polymorphic |
| TD-07 | **Dual user management systems** -- JPA entities (User/Role in tenant schema) + JDBC POJOs (PlatformUser, Tenant in platform schema) | core/tenancy | Two different data access patterns for similar concepts; inconsistent coding style |
| TD-08 | **BCryptPasswordEncoder instantiated directly** instead of as Spring bean | `UserService.java`, `PlatformUserService.java` | Not configurable; each service creates its own instance |
| TD-09 | **`dev` profile configured for PostgreSQL** but CLAUDE.md and README describe it as "H2 en memoria" | `application-dev.yml` | Misleading documentation; H2 migrations exist but dev profile uses PostgreSQL |
| TD-10 | **Module `core/audit`** is a stub (only package-info.java) but `audit_logs` table exists in migrations | `core/audit/` | No actual audit implementation despite table and module dependency declarations |
| TD-11 | **Module `core/files`** is a stub (only package-info.java) but `files_meta` table exists | `core/files/` | Same as above -- placeholder with no implementation |
| TD-12 | **Module `core/form`** is a stub (only package-info.java) | `core/form/` | Declared module with no implementation |

### 15.3 Coupling Concerns

| # | Issue | Location | Impact |
|---|-------|----------|--------|
| TD-13 | **ui-zk-app depends directly on mod-customers and mod-employees** | `ui/zk-app/pom.xml` | Violates modular monolith principle of ADR-0002; UI should communicate through interfaces/APIs, not direct service dependencies |
| TD-14 | **SalesService directly invokes WorkflowService** | `SalesService.java` | Tight coupling between business module and workflow core; if workflow module is absent, sales cannot function |
| TD-15 | **OutboxScheduler iterates tenants from YAML list** (`tenantProperties.getTenants()`) instead of from BD active tenants | `OutboxScheduler.java` | Inconsistent with TenantBootstrapRunner which reads from BD; newly created tenants (not in YAML) won't have outbox processed |

### 15.4 Missing Features / Stubs

| # | Feature | Current State | Notes |
|---|---------|---------------|-------|
| TD-16 | External event publishing (Kafka) | NoOp implementation | SPI defined, ready for implementation |
| TD-17 | ABAC authorization | `AbacEvaluator.java` exists but is empty/stub | Only RBAC is functional |
| TD-18 | File storage (S3/MinIO) | Table + Docker Compose configured, no Java implementation | `core/files` is stub |
| TD-19 | Form Builder (JSON Schema) | Module declared, no implementation | `core/form` is stub |
| TD-20 | Integration tests | Testcontainers dependencies declared, no test classes found | Only spring-boot-starter-test in scope |
| TD-21 | `JwtDecoder` throws exception in OIDC mode | `SecurityConfig.jwtDecoder()` | If Spring Boot auto-config runs before the custom bean in OIDC mode, the manual `throw` is hit; fragile conditional logic |

### 15.5 Observability Gaps

| # | Issue | Impact |
|---|-------|--------|
| TD-22 | No distributed tracing (Micrometer/OpenTelemetry) | Cannot trace requests across layers |
| TD-23 | ModulesHealthIndicator always returns UP even if module NOT_LOADED | `health()` line 45: uses `Health.up()` for both branches |
| TD-24 | No metrics export (Prometheus/Grafana) | Only basic actuator endpoints exposed |

---

## 16. Risk Assessment

| Risk | Probability | Impact | Mitigation Strategy |
|------|-------------|--------|---------------------|
| Tenant data leakage via TenantContext ThreadLocal leak | Low | Critical | ThreadLocal cleared in finally blocks; but async/virtual thread scenarios untested |
| SQL injection in SET search_path | Very Low | Critical | Tenant name validated with strict regex; no user input reaches SQL directly |
| Hardcoded admin credentials in production | High | High | Move seed credentials to env vars or secrets manager; disable seeding in prod profile |
| Flowable shared schema limits multi-tenant isolation | Medium | Medium | Flowable uses tenantId field; but a bug could expose cross-tenant workflow data |
| Schema count scaling limit (~500) | Low (V1) | Medium | Monitor tenant count; plan BD-per-tenant migration path for large scale |
| ZK UI session hijacking (no CSRF, permitAll) | Medium | Medium | Implement CSRF for ZK; add session fixation protection |
| Outbox scheduler missing newly created tenants | Medium | Low | Switch OutboxScheduler to use TenantRegistryService instead of YAML config |
| No test coverage | High | Medium | Write integration tests using Testcontainers before production |

---

## 17. Diagrams Summary

### 17.1 Filter Chain Order

```
Request  -->  RequestIdFilter (HIGHEST_PRECEDENCE)
         -->  ZulForwardFilter (HIGHEST_PRECEDENCE, /zul/* only)
         -->  TenantFilter (HIGHEST_PRECEDENCE + 10, skips /zul/**)
         -->  ZK SecurityFilterChain (Order=1, /zul/** /zkau/** /login /)
         -->  API SecurityFilterChain (Order=2, everything else)
              |-- BearerTokenAuthenticationFilter
              |-- JwtTenantFilter (after Bearer)
         -->  DispatcherServlet / DHtmlLayoutServlet
```

### 17.2 Maven Module Build Order

```
1. shared-libs/domain-core
2. shared-libs/common-dto
3. shared-libs/mapping
4. shared-libs/utils
5. core/tenancy
6. core/auth-security
7. core/catalogs
8. core/files
9. core/audit
10. core/events
11. core/form
12. core/workflow
13. modules/customers
14. modules/employees
15. modules/documents
16. modules/sales
17. ui/zk-app
18. platform-app
```

---

## 18. Glossary

| Term | Definition |
|------|-----------|
| **Tenant** | An organization/company that uses the platform; data isolated in its own PostgreSQL schema |
| **Platform Admin** | Super-admin who manages all tenants, schemas, and platform users; stored in `platform.platform_users` |
| **Tenant User** | A user within a specific tenant; stored in `<tenant>.users` |
| **Schema Type** | Classification of modules: `core`, `hr`, `sales`, `documents`; controls menu visibility per role |
| **Outbox Event** | Domain event persisted in `outbox_events` table for reliable eventual delivery |
| **Business Key** | Human-readable identifier for a Flowable process instance (e.g., order number) |

---

## 19. Next Steps & Recommendations

### Immediate Actions (V1 Stabilization)
- [ ] Fix hardcoded admin credentials (TD-01) -- use env vars, disable in prod
- [ ] Close Statement objects in SchemaMultiTenantConnectionProvider (TD-04)
- [ ] Fix OutboxScheduler to use BD tenant list instead of YAML (TD-15)
- [ ] Fix ModulesHealthIndicator to report DOWN when modules are missing (TD-23)
- [ ] Write integration tests for multi-tenancy, auth, and Flowable (TD-20)
- [ ] Clarify dev profile: reconcile CLAUDE.md/README with actual PostgreSQL config (TD-09)

### Short-Term (V1.1)
- [ ] Implement core/audit module (audit logging via AOP or events)
- [ ] Implement core/files module (S3/MinIO file storage)
- [ ] Add CSRF protection for ZK UI (TD-06-security)
- [ ] Add distributed tracing (Micrometer + OpenTelemetry)
- [ ] Decide: adopt Result type pattern or remove it (TD-05)

### Medium-Term (V2)
- [ ] Implement Kafka external event publisher
- [ ] Implement ABAC authorization
- [ ] Implement core/form module (JSON Schema form builder)
- [ ] Decouple ui-zk-app from business module direct dependencies (TD-13)
- [ ] Consider Java 21 migration and virtual thread compatibility (TD-03)

---

**Document History**:
- 2026-03-06: Complete architectural baseline from codebase analysis (all 20 modules, 3 ADRs, all configuration files, all Java sources, all SQL migrations, all ZUL views, BPMN process definitions)
