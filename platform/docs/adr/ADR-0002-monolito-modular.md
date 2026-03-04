# ADR-0002: Monolito modular como arquitectura inicial

## Estado
Aceptada

## Contexto
Se necesita entregar la V1 de la plataforma rápidamente, con la posibilidad de evolucionar a microservicios cuando el equipo y la carga lo justifiquen.

## Decisión
Se adopta un **monolito modular** con Maven multi-módulo.

## Razones
- **Velocidad de desarrollo**: un solo deploy, debugging simple, sin latencia de red entre módulos.
- **Módulos bien separados**: cada módulo es un JAR con su propio package, dependencias explícitas en Maven.
- **Ports & Adapters**: cada módulo expone interfaces (ports) que permiten extraerlo a servicio independiente sin cambiar lógica interna.
- **Un solo Spring Boot context**: component-scan, transacciones, eventos internos funcionan sin infraestructura adicional.
- **Menor costo operacional**: no se necesita service discovery, circuit breakers, ni orquestación de contenedores en V1.

## Consecuencias
- Los módulos NO deben acceder a internos de otros módulos (solo interfaces públicas).
- Al extraer un módulo se deberá:
  1. Crear su propio `@SpringBootApplication`
  2. Reemplazar llamadas internas por REST/gRPC/mensajería
  3. Mover sus migraciones Flyway
- El `platform-app` es el punto de ensamblaje que depende de todos los módulos.

## Diagrama ASCII

```
+-----------------------------------------------------------+
|                    platform-app (JAR)                      |
|  +--------+  +--------+  +----------+  +-----------+      |
|  |  core  |  |  core  |  |  modules |  |    ui     |      |
|  | tenancy|  |security|  |customers |  |  zk-app   |      |
|  +--------+  +--------+  +----------+  +-----------+      |
|  +--------+  +--------+  +----------+  +-----------+      |
|  |  core  |  |  core  |  |  modules |  |  shared   |      |
|  | events |  |workflow|  |  sales   |  | libs (4)  |      |
|  +--------+  +--------+  +----------+  +-----------+      |
+-----------------------------------------------------------+
                         |
                    PostgreSQL
              (schema por tenant + platform)
```
