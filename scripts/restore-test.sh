#!/usr/bin/env bash
# Restores into a separate database and preserves the live database and its volume.
set -euo pipefail
TASK_ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$TASK_ROOT"
[[ $# -eq 2 ]] || { echo 'Uso: bash scripts/restore-test.sh backups/arquivo.dump banco_novo_de_teste' >&2; exit 1; }
TASK_DUMP=$1
TASK_TARGET=$2
[[ -f "$TASK_DUMP" && "$TASK_TARGET" =~ ^[a-z][a-z0-9_]{2,50}$ ]] || { echo 'Informe um backup existente e nome válido de banco novo.' >&2; exit 1; }
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
[[ "$TASK_TARGET" != "$POSTGRES_DB" && "$TASK_TARGET" != postgres && "$TASK_TARGET" != template0 && "$TASK_TARGET" != template1 ]] || { echo 'Escolha um banco novo, separado do banco em uso.' >&2; exit 1; }
docker compose --env-file .env -f "$COMPOSE_CONFIG" exec -T postgres createdb -U "$POSTGRES_USER" "$TASK_TARGET"
docker compose --env-file .env -f "$COMPOSE_CONFIG" exec -T postgres pg_restore -U "$POSTGRES_USER" --dbname="$TASK_TARGET" --exit-on-error --no-owner < "$TASK_DUMP"
docker compose --env-file .env -f "$COMPOSE_CONFIG" exec -T postgres psql -X -U "$POSTGRES_USER" -d "$TASK_TARGET" -v ON_ERROR_STOP=1 -c 'SELECT version,success FROM flyway_schema_history ORDER BY installed_rank; SELECT count(*) AS products FROM product; SELECT count(*) AS sales FROM sale;'
echo "Restauração concluída em $TASK_TARGET. O banco da aplicação foi preservado."
