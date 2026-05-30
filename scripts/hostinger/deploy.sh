#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────
# CafeQR 2.0 — Manual Deploy Script
# ──────────────────────────────────────────────────────────────
# Pulls latest Docker images and restarts the stack.
#
# Usage:
#   ./deploy.sh              # Deploy all services
#   ./deploy.sh backend      # Deploy only backend
#   ./deploy.sh frontend     # Deploy only frontend
# ──────────────────────────────────────────────────────────────
set -euo pipefail

APP_DIR="/home/cafeqr/app"
COMPOSE_FILE="${APP_DIR}/docker-compose.prod.yml"

cd "${APP_DIR}"

SERVICE="${1:-all}"

echo "╔══════════════════════════════════════════════════════════╗"
echo "║    CafeQR 2.0 — Production Deploy                       ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

case "${SERVICE}" in
  backend)
    echo "Deploying backend only..."
    docker compose -f "${COMPOSE_FILE}" pull backend
    docker compose -f "${COMPOSE_FILE}" up -d --no-deps backend
    ;;
  frontend)
    echo "Deploying frontend only..."
    docker compose -f "${COMPOSE_FILE}" pull frontend
    docker compose -f "${COMPOSE_FILE}" up -d --no-deps frontend
    ;;
  all)
    echo "Deploying all services..."
    docker compose -f "${COMPOSE_FILE}" pull backend frontend
    docker compose -f "${COMPOSE_FILE}" up -d --remove-orphans
    ;;
  *)
    echo "Unknown service: ${SERVICE}"
    echo "Usage: $0 [backend|frontend|all]"
    exit 1
    ;;
esac

echo ""
echo "Waiting 10 seconds for services to start..."
sleep 10

echo ""
echo "── Service Status ──"
docker compose -f "${COMPOSE_FILE}" ps

echo ""
echo "── Health Checks ──"
for svc in backend frontend db redis rabbitmq caddy; do
  STATUS=$(docker inspect --format='{{.State.Health.Status}}' "cafeqr-${svc}" 2>/dev/null || echo "no-healthcheck")
  printf "  %-12s %s\n" "${svc}:" "${STATUS}"
done

echo ""
echo "✅ Deploy complete!"
