#!/bin/bash
# Script para automatizar el despliegue del módulo Customer Loyalty Points
# Autor: Lily Reyes

set -e

echo "--------------------------------------------------------"
echo "🚀 Iniciando despliegue de Odoo 17 + PostgreSQL..."
echo "--------------------------------------------------------"

# 1. Levantar contenedores
docker compose up -d

echo "--------------------------------------------------------"
echo "⏳ Esperando 10 segundos para que los servicios inicien..."
sleep 10
echo "--------------------------------------------------------"

# 2. Mostrar estado de los contenedores
docker compose ps

echo ""
echo "✅ ¡Entorno listo!"
echo "--------------------------------------------------------"
echo "Pasos finales para tu portafolio:"
echo "1. Ve a http://localhost:8069"
echo "2. Crea una base de datos (Ej: 'loyalty_db')"
echo "3. Activa el modo desarrollador (Ajustes)"
echo "4. Ve a Aplicaciones > Actualizar lista de aplicaciones"
echo "5. Busca 'Customer Loyalty Points' e instálalo"
echo "--------------------------------------------------------"
echo "Para ver los logs en tiempo real usa: docker compose logs -f web"
