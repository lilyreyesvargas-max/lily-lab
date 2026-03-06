# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Stack

- **Java 17**, **Spring Boot 3.3.5**, **ZK 10 (jakarta)** MVVM, **Flowable 7** BPMN, **PostgreSQL 16**
- **Flyway** (run programmatically per tenant), **MapStruct + Lombok**, **SpringDoc/OpenAPI**
- Multi-tenancy: one PostgreSQL schema per tenant, resolved via `TenantContext` (ThreadLocal)

## Commands

```bash
# Build (skip tests)
mvn -q -DskipTests package

# Run with H2-in-memory (no Docker needed) — NOTE: profile is named 'dev' but uses PostgreSQL;
# use 'h2' profile if it exists, otherwise 'dev' requires a running PostgreSQL
SPRING_PROFILES_ACTIVE=dev mvn -pl platform-app spring-boot:run

# Run with PostgreSQL (start DB first)
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=local mvn -pl platform-app spring-boot:run

# Run all tests
mvn test

# Run tests for a single module
mvn test -pl modules/customers

# Run a single test class
mvn test -pl modules/customers -Dtest=CustomerServiceTest

# Integration tests (requires Docker/Testcontainers)
mvn verify -Plocal
```

## Architecture

This is a **modular monolith** (microkernel pattern) assembled by `platform-app`. Build order enforced by Maven:

```
shared-libs  →  core  →  modules  →  ui  →  platform-app
```

### Layer responsibilities

| Layer | Artifact prefix | Purpose |
|-------|----------------|---------|
| `shared-libs/` | `shared-*` | Pure Java: `BaseEntity`, `DomainEvent`, `PageResponse`, `ApiResponse`, MapStruct base |
| `core/` | `core-*` | Cross-cutting concerns: auth, tenancy, events, catalogs, files, audit, form, workflow |
| `modules/` | `mod-*` | Business modules (customers, employees, documents, sales) |
| `ui/zk-app/` | `ui-zk-app` | ZK MVVM ViewModels and ZUL pages only; depends on `core-*` + `mod-*` |
| `platform-app/` | — | Spring Boot main class + config + Flyway migrations |

### Multi-tenancy

- `TenantFilter` (HTTP) or `JwtTenantFilter` extracts the tenant from request/JWT and calls `TenantContext.setCurrentTenant(id)`.
- Hibernate uses `TenantIdentifierResolver` + `SchemaMultiTenantConnectionProvider` to set `search_path = <tenant>` per query.
- On startup, `TenantBootstrapRunner` migrates the `platform` schema (global admin tables), seeds tenants from YAML, then runs Flyway per tenant schema.
- Add new tenants by appending to `app.tenants` list in the active profile YAML and restarting.

### Domain events (Outbox pattern)

- Services publish events via `DomainEventPublisher` → stored in `outbox_events` table.
- `OutboxScheduler` polls and dispatches via `ExternalEventPublisher` (default: no-op; replace with Kafka impl).
- Event classes extend `com.lreyes.platform.shared.domain.DomainEvent` and live in a `event/` sub-package of their module.

### Security

- **Dev profile**: static HS256 JWT signed with `app.security.jwt-secret`; issue tokens via `POST /api/auth/login`.
- **Prod profile**: OIDC/JWT via `OIDC_ISSUER_URI`; `JwtAuthConverter` maps claims to Spring Security roles.
- Role constants are in `RoleConstants`; ABAC stub in `AbacEvaluator`.

### Business module pattern

Each module (e.g. `customers`) uses a flat package with:
- `Customer.java` — `@Entity` extending `BaseEntity`
- `CustomerRepository.java` — Spring Data JPA
- `CustomerService.java` — `@Service`, uses `TenantContext`, publishes `DomainEvent`
- `CustomerController.java` — `@RestController` at `/api/{module}`
- `CustomerMapper.java` — MapStruct interface
- `dto/` — request/response DTOs
- `event/` — domain event classes

### ZK UI

- ZUL files: `ui/zk-app/src/main/resources/web/zul/`
- ViewModels: `com.lreyes.platform.ui.zk.vm.*VM` (suffix `VM`)
- ZUL root component binds with `viewModel="@id('vm') @init('com.lreyes.platform.ui.zk.vm.XxxVM')"`
- UI-local model classes (not JPA entities) live in `com.lreyes.platform.ui.zk.model`

### Database migrations

Flyway scripts live in `platform-app/src/main/resources/db/migration/`:
- `h2/` — used when the `h2` profile is active (in-memory dev)
- Versioned as `V1__core_schema.sql`, `V2__events_outbox.sql`, etc.
- Migrations run per tenant schema; add new scripts with the next version number.

## Key configuration

| Property | Location | Notes |
|----------|----------|-------|
| `app.tenants` | profile YAML | Comma-separated tenant names seeded on startup |
| `app.security.mode` | profile YAML | `jwt-local` (dev) or `oidc` (prod) |
| `app.security.jwt-secret` | profile YAML | Dev only — minimum 256-bit key |
| `DB_HOST/DB_USER/DB_PASSWORD` | env vars | Required for `local`/`prod` profiles |

## Package root

`com.lreyes.platform`
