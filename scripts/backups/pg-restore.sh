#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────
# CafeQR 2.0 — PostgreSQL Restore Script
# ──────────────────────────────────────────────────────────────
# Restores a PostgreSQL backup from a compressed dump file.
#
# Usage:
#   ./pg-restore.sh /home/cafeqr/backups/postgres/cafeqr_pos_db_20260531_030000.sql.gz
# ──────────────────────────────────────────────────────────────
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "Usage: $0 <backup-file.sql.gz>"
  echo ""
  echo "Available backups:"
  ls -lh /home/cafeqr/backups/postgres/cafeqr_*.sql.gz 2>/dev/null || echo "  No backups found."
  exit 1
fi

BACKUP_FILE="$1"
DB_CONTAINER="cafeqr-db"
DB_NAME="${DB_NAME:-pos_db}"
DB_USER="${DB_USER:-cafeqr_prod}"

if [ ! -f "${BACKUP_FILE}" ]; then
  echo "ERROR: Backup file not found: ${BACKUP_FILE}"
  exit 1
fi

echo "╔══════════════════════════════════════════════════════════╗"
echo "║  ⚠️  DATABASE RESTORE — THIS IS DESTRUCTIVE!            ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  File: $(basename "${BACKUP_FILE}")"
echo "║  Size: $(du -h "${BACKUP_FILE}" | cut -f1)"
echo "║  Database: ${DB_NAME}"
echo "║                                                          ║"
echo "║  This will REPLACE ALL DATA in the database.             ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""
read -rp "Are you sure you want to continue? (type 'yes' to confirm): " CONFIRM
if [ "${CONFIRM}" != "yes" ]; then
  echo "Restore cancelled."
  exit 0
fi

echo "[$(date)] Creating pre-restore backup..."
PRE_RESTORE_FILE="/home/cafeqr/backups/postgres/pre_restore_$(date +%Y%m%d_%H%M%S).sql.gz"
docker exec "${DB_CONTAINER}" \
  pg_dump -U "${DB_USER}" -d "${DB_NAME}" --no-owner --no-privileges \
  | gzip -9 > "${PRE_RESTORE_FILE}"
echo "[$(date)] Pre-restore backup saved: ${PRE_RESTORE_FILE}"

echo "[$(date)] Restoring database from: $(basename "${BACKUP_FILE}")..."
gunzip -c "${BACKUP_FILE}" | docker exec -i "${DB_CONTAINER}" \
  psql -U "${DB_USER}" -d "${DB_NAME}" --quiet --single-transaction

echo ""
echo "[$(date)] ✅ Database restored successfully!"
echo "Pre-restore backup: ${PRE_RESTORE_FILE}"
echo ""
echo "IMPORTANT: Restart the backend to clear caches:"
echo "  docker compose -f docker-compose.prod.yml restart backend"
