#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────
# CafeQR 2.0 — Setup Backup Cron Job
# ──────────────────────────────────────────────────────────────
# Run once to install the automated daily database backup cron.
# ──────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKUP_SCRIPT="${SCRIPT_DIR}/pg-backup.sh"
LOG_FILE="/var/log/cafeqr-backup.log"
CRON_SCHEDULE="0 3 * * *"   # 3:00 AM IST daily

# Ensure backup script is executable
chmod +x "${BACKUP_SCRIPT}"

# Create log file
sudo touch "${LOG_FILE}"
sudo chown "$(whoami):$(whoami)" "${LOG_FILE}"

# Install cron job (idempotent — removes old entry first)
CRON_LINE="${CRON_SCHEDULE} ${BACKUP_SCRIPT} >> ${LOG_FILE} 2>&1"
(crontab -l 2>/dev/null | grep -v "pg-backup.sh"; echo "${CRON_LINE}") | crontab -

echo "✅ Backup cron installed:"
echo "   Schedule: Daily at 3:00 AM"
echo "   Script:   ${BACKUP_SCRIPT}"
echo "   Log:      ${LOG_FILE}"
echo ""
echo "Verify with: crontab -l"
