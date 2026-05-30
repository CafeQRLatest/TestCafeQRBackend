#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────
# CafeQR 2.0 — PostgreSQL Automated Backup Script
# ──────────────────────────────────────────────────────────────
# Creates compressed PostgreSQL dumps and enforces retention.
#
# Usage:
#   ./pg-backup.sh                    # Manual run
#   crontab: 0 3 * * * /path/to/pg-backup.sh >> /var/log/cafeqr-backup.log 2>&1
# ──────────────────────────────────────────────────────────────
set -euo pipefail

# ── Configuration ──
BACKUP_DIR="/home/cafeqr/backups/postgres"
RETENTION_DAYS=7
DB_CONTAINER="cafeqr-db"
DB_NAME="${DB_NAME:-pos_db}"
DB_USER="${DB_USER:-cafeqr_prod}"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="cafeqr_${DB_NAME}_${TIMESTAMP}.sql.gz"

# ── Setup ──
mkdir -p "${BACKUP_DIR}"

echo "[$(date)] Starting PostgreSQL backup..."

# ── Create Backup ──
docker exec "${DB_CONTAINER}" \
  pg_dump -U "${DB_USER}" -d "${DB_NAME}" \
    --no-owner --no-privileges --clean --if-exists \
  | gzip -9 > "${BACKUP_DIR}/${BACKUP_FILE}"

BACKUP_SIZE=$(du -h "${BACKUP_DIR}/${BACKUP_FILE}" | cut -f1)
echo "[$(date)] Backup created: ${BACKUP_FILE} (${BACKUP_SIZE})"

# ── Verify Backup (basic integrity check) ──
if gzip -t "${BACKUP_DIR}/${BACKUP_FILE}" 2>/dev/null; then
  echo "[$(date)] Backup integrity: OK"
else
  echo "[$(date)] ERROR: Backup file is corrupt!"
  exit 1
fi

# ── Enforce Retention ──
DELETED_COUNT=0
while IFS= read -r old_file; do
  rm -f "${old_file}"
  DELETED_COUNT=$((DELETED_COUNT + 1))
  echo "[$(date)] Deleted old backup: $(basename "${old_file}")"
done < <(find "${BACKUP_DIR}" -name "cafeqr_*.sql.gz" -type f -mtime +${RETENTION_DAYS})

echo "[$(date)] Retention cleanup: ${DELETED_COUNT} old backups removed."

# ── Summary ──
TOTAL_BACKUPS=$(find "${BACKUP_DIR}" -name "cafeqr_*.sql.gz" -type f | wc -l)
TOTAL_SIZE=$(du -sh "${BACKUP_DIR}" | cut -f1)
echo "[$(date)] Backup complete. Total backups: ${TOTAL_BACKUPS}, Total size: ${TOTAL_SIZE}"
