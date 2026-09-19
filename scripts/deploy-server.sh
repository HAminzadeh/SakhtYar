#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="docker-compose.server.yml"

if [[ ! -f ".env" ]]; then
  echo ".env not found."
  echo "Copy .env.server.example to .env and set production secrets."
  exit 1
fi

docker compose -f "${COMPOSE_FILE}" pull || true
docker compose -f "${COMPOSE_FILE}" build --pull
docker compose -f "${COMPOSE_FILE}" up -d --remove-orphans

echo
echo "SakhtYar deployment completed."
docker compose -f "${COMPOSE_FILE}" ps
