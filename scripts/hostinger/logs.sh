#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────
# CafeQR 2.0 — View Service Logs
# ──────────────────────────────────────────────────────────────
# Usage:
#   ./logs.sh                  # All services (live tail)
#   ./logs.sh backend          # Backend only
#   ./logs.sh backend 100      # Backend, last 100 lines
#   ./logs.sh db               # Database logs
# ──────────────────────────────────────────────────────────────
set -euo pipefail

APP_DIR="/home/cafeqr/app"
COMPOSE_FILE="${APP_DIR}/docker-compose.prod.yml"
SERVICE="${1:-}"
LINES="${2:-50}"

if [ -z "${SERVICE}" ]; then
  echo "Tailing all service logs (Ctrl+C to stop)..."
  docker compose -f "${COMPOSE_FILE}" logs --tail="${LINES}" -f
else
  echo "Tailing ${SERVICE} logs (last ${LINES} lines, Ctrl+C to stop)..."
  docker compose -f "${COMPOSE_FILE}" logs --tail="${LINES}" -f "${SERVICE}"
fi
