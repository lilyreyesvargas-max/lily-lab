# Platform - Plataforma Base Modular

Monolito modular en Java 17 + Spring Boot 3.3, con multi-tenancy por esquema PostgreSQL,
ZK (MVVM) para UI, Flowable para workflows BPMN, y arquitectura preparada para microservicios.

## Arquitectura

```
platform/
├── shared-libs/             # Librerías compartidas
│   ├── domain-core/         #   Entidades base, Result, Errors
│   ├── common-dto/          #   DTOs compartidos, PageResponse
│   ├── mapping/             #   MapStruct config y mappers
│   └── utils/               #   Utilidades comunes
├── core/                    # Microkernel
│   ├── auth-security/       #   OIDC/JWT, RBAC, ABAC stub
│   ├── tenancy/             #   Multi-tenant resolver, Flyway por schema
│   ├── catalogs/            #   Nomencladores multi-tenant
│   ├── files/               #   Almacenamiento S3/MinIO/FS
│   ├── audit/               #   Auditoría de entidades y acciones
│   ├── events/              #   Eventos de dominio + interfaz Kafka
│   ├── form/                #   Form Builder (JSON Schema)
│   └── workflow/            #   Flowable BPMN config y endpoints
├── modules/                 # Módulos de negocio
│   ├── customers/           #   Gestión de clientes
│   ├── employees/           #   Gestión de empleados
│   ├── documents/           #   DMS simple
│   └── sales/               #   Ventas + aprobación Flowable
├── ui/
│   └── zk-app/              # Interfaz ZK MVVM
├── platform-app/            # Spring Boot runnable (ensambla todo)
├── docs/                    # ADRs y documentación
│   └── adr/
├── docker-compose.yml       # Postgres, Keycloak (opt), MinIO (opt)
└── pom.xml                  # Parent POM
```

## Requisitos

- **Java 17+** (configurado para 17; cambiar a 21 en `pom.xml` si disponible)
- **Maven 3.8+**
- **Docker + Docker Compose** (para perfil `local` con PostgreSQL)
- **PostgreSQL 16** (vía Docker o instalación local)

## Inicio rápido

### Perfil `dev` (H2 en memoria, sin Docker)

```bash
# Compilar
mvn -q -DskipTests package

# Ejecutar
SPRING_PROFILES_ACTIVE=dev mvn -pl platform-app spring-boot:run
```

### Perfil `local` (PostgreSQL con Docker)

```bash
# 1. Levantar PostgreSQL
docker compose up -d postgres

# 2. Compilar
mvn -q -DskipTests package

# 3. Ejecutar
SPRING_PROFILES_ACTIVE=local mvn -pl platform-app spring-boot:run
```

### Perfil `prod`

Requiere configurar variables de entorno:
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- `OIDC_ISSUER_URI`, `OIDC_JWK_SET_URI`
- `APP_TENANTS` (lista de tenants separados por coma)

## Agregar un nuevo tenant

1. Agregar el nombre del tenant a `app.tenants` en el YAML del perfil activo.
2. Reiniciar la aplicación — `TenantBootstrapRunner` creará el schema y ejecutará migraciones.
3. (Alternativa) Llamar al endpoint `POST /api/admin/tenants/{nombre}` (cuando esté implementado).

## Tests

```bash
# Todos los tests
mvn test

# Solo tests de integración (requiere Docker para Testcontainers)
mvn verify -Plocal
```

## Documentación

- [ADR-0001: Multi-tenant por esquema](docs/adr/ADR-0001-multi-tenant-por-esquema.md)
- [ADR-0002: Monolito modular](docs/adr/ADR-0002-monolito-modular.md)
- [ADR-0003: Flowable como motor BPMN](docs/adr/ADR-0003-flowable-eleccion-v1.md)

## Diagramas

### Flujo de autenticación y resolución de tenant

```
  Cliente HTTP
       |
       v
  [Request + JWT / X-Tenant-Id]
       |
       v
  TenantFilter (extrae tenant de JWT claim o header)
       |
       v
  TenantContext.setCurrentTenant("acme")
       |
       v
  Spring Security (valida JWT / OIDC token)
       |
       v
  Controller -> Service -> Repository
       |                        |
       |                  Hibernate
       |              SET search_path = acme
       |                        |
       v                        v
  Response               PostgreSQL (schema: acme)
```

### Estructura de schemas en PostgreSQL

```
  PostgreSQL (database: platform)
  ├── schema: platform     <- Tablas Flowable (act_*)
  ├── schema: acme         <- Tablas de negocio tenant "acme"
  │   ├── users
  │   ├── roles
  │   ├── customers
  │   ├── employees
  │   └── ...
  ├── schema: globex       <- Tablas de negocio tenant "globex"
  │   ├── users
  │   ├── roles
  │   ├── customers
  │   └── ...
  └── schema: public       <- (no usado)
```
