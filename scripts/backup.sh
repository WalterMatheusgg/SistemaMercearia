#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$TASK_ROOT"
[[ -f .env ]] || { echo 'Arquivo .env não encontrado.' >&2; exit 1; }
set -a
. "$TASK_ROOT/.env"
set +a
# Sem seletor, continua usando o Compose local. Em produção, selecione explicitamente
# infra/compose.production.yaml; nunca misture os dois volumes de banco.
COMPOSE_CONFIG=${COMPOSE_CONFIG:-compose.yaml}
case "$COMPOSE_CONFIG" in
  compose.yaml|infra/compose.production.yaml) ;;
  *) echo 'COMPOSE_CONFIG inválido; use compose.yaml ou infra/compose.production.yaml.' >&2; exit 1 ;;
esac
umask 077
mkdir -p backups
TASK_BACKUP="backups/ferragens-$(date -u +%Y%m%dT%H%M%SZ).dump"
TASK_TEMP="${TASK_BACKUP}.partial"
trap 'rm -f -- "$TASK_TEMP"' EXIT
docker compose --env-file .env -f "$COMPOSE_CONFIG" exec -T postgres pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner > "$TASK_TEMP"
[[ -s "$TASK_TEMP" ]] || { echo 'Backup vazio; operação interrompida.' >&2; exit 1; }
mv -- "$TASK_TEMP" "$TASK_BACKUP"
echo "$TASK_BACKUP"
