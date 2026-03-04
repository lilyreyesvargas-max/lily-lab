#!/usr/bin/env bash
# ==============================================================================
# stop-local.sh — Detiene los servicios Docker de la plataforma
#
# Uso:
#   ./scripts/stop-local.sh          # Detener servicios (preservar datos)
#   ./scripts/stop-local.sh --clean  # Detener y borrar volúmenes
# ==============================================================================
set -euo pipefail

cd "$(dirname "$0")/.."

case "${1:-}" in
    --clean)
        echo ">>> Deteniendo servicios y eliminando volúmenes..."
        docker compose --profile app down -v
        echo ">>> Limpieza completa."
        ;;
    *)
        echo ">>> Deteniendo servicios (datos preservados en volúmenes)..."
        docker compose --profile app down
        echo ">>> Servicios detenidos. Datos preservados."
        echo "    Para borrar datos: $0 --clean"
        ;;
esac
