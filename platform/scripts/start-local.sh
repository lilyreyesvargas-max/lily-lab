#!/usr/bin/env bash
# ==============================================================================
# start-local.sh — Levanta la infraestructura local y ejecuta la app
#
# Uso:
#   ./scripts/start-local.sh              # Solo infra (postgres)
#   ./scripts/start-local.sh --keycloak   # Infra + Keycloak
#   ./scripts/start-local.sh --all        # Infra + Keycloak + MinIO
#   ./scripts/start-local.sh --app        # Todo + app en Docker
# ==============================================================================
set -euo pipefail

cd "$(dirname "$0")/.."

SERVICES="postgres"

case "${1:-}" in
    --keycloak)
        SERVICES="postgres keycloak"
        ;;
    --all)
        SERVICES="postgres keycloak minio"
        ;;
    --app)
        echo ">>> Construyendo imagen Docker..."
        docker compose build platform-app
        echo ">>> Levantando todos los servicios..."
        docker compose --profile app up -d
        echo ""
        echo "=== Servicios activos ==="
        echo "  PostgreSQL:  localhost:5432"
        echo "  Keycloak:    http://localhost:8180 (admin/admin)"
        echo "  MinIO:       http://localhost:9001 (minioadmin/minioadmin)"
        echo "  Platform:    http://localhost:8080"
        echo "  Swagger UI:  http://localhost:8080/swagger-ui/index.html"
        echo ""
        echo ">>> Logs: docker compose logs -f platform-app"
        exit 0
        ;;
    "")
        ;;
    *)
        echo "Uso: $0 [--keycloak|--all|--app]"
        exit 1
        ;;
esac

echo ">>> Levantando: ${SERVICES}"
docker compose up -d ${SERVICES}

echo ""
echo ">>> Esperando que PostgreSQL esté listo..."
docker compose exec postgres pg_isready -U platform -d platform 2>/dev/null || \
    sleep 3 && echo "    (esperando...)" && \
    docker compose exec postgres pg_isready -U platform -d platform

echo ""
echo "=== Infraestructura lista ==="
echo "  PostgreSQL: localhost:5432 (platform/platform)"
if [[ "$SERVICES" == *"keycloak"* ]]; then
    echo "  Keycloak:   http://localhost:8180 (admin/admin)"
    echo "              Realm: platform | Client: platform-app"
fi
if [[ "$SERVICES" == *"minio"* ]]; then
    echo "  MinIO:      http://localhost:9001 (minioadmin/minioadmin)"
fi
echo ""
echo ">>> Para ejecutar la app localmente:"
echo "    mvn -pl platform-app spring-boot:run -Dspring-boot.run.profiles=local"
echo ""
echo ">>> Para parar: ./scripts/stop-local.sh"
