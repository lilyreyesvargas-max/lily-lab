#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Clinical Management System — Automated Backup Script
# Schedule via cron: 0 2 * * * /path/to/scripts/backup_cron.sh >> /var/log/cms_backup.log 2>&1
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BACKUP_DIR="$PROJECT_DIR/backups"
DB_NAME="odoo_clinic"
RETENTION_DAYS=30
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_FILE="$BACKUP_DIR/${DB_NAME}_${TIMESTAMP}.sql.gz"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }

# ─── Ensure backup directory exists ──────────────────────────────────────────
mkdir -p "$BACKUP_DIR"

log "Starting backup of $DB_NAME → $BACKUP_FILE"

# ─── Run backup via docker compose ───────────────────────────────────────────
cd "$PROJECT_DIR"
sudo docker compose exec -T db pg_dump -U odoo "$DB_NAME" | gzip > "$BACKUP_FILE"

if [ $? -eq 0 ]; then
    SIZE=$(du -sh "$BACKUP_FILE" | cut -f1)
    log "Backup completed successfully. Size: $SIZE"
else
    log "ERROR: Backup failed!"
    exit 1
fi

# ─── Rotate old backups (keep last RETENTION_DAYS days) ──────────────────────
log "Rotating backups older than $RETENTION_DAYS days..."
find "$BACKUP_DIR" -name "${DB_NAME}_*.sql.gz" -mtime +$RETENTION_DAYS -delete
REMAINING=$(find "$BACKUP_DIR" -name "${DB_NAME}_*.sql.gz" | wc -l)
log "Rotation complete. $REMAINING backup(s) retained."

log "Done."
