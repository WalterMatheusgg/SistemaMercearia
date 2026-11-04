#!/usr/bin/env bash
# Verificação somente leitura: não cria, modifica nem exclui dados.
set -euo pipefail
BASE_URL="${SMOKE_BASE_URL:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}"
MAILPIT_URL="${SMOKE_MAILPIT_URL:-}"
command -v curl >/dev/null || { echo "curl não encontrado" >&2; exit 1; }
TEMP_DIR=$(mktemp -d)
trap 'rm -rf -- "$TEMP_DIR"' EXIT

http_status() {
  curl --silent --show-error --max-time 10 --output /dev/null --write-out '%{http_code}' "$1"
}
echo "Aguardando o Spring Boot responder em ${BASE_URL}..."
READY=0
for attempt in $(seq 1 60); do
  CODE=$(http_status "${BASE_URL}/actuator/health" 2>/dev/null || true)
  if [[ "$CODE" == 200 ]]; then READY=1; break; fi
  sleep 2
done
[[ "$READY" == 1 ]] || { echo "ERRO: endpoint de saúde indisponível" >&2; exit 1; }
echo "OK · Spring Boot e PostgreSQL (HTTP 200)"

curl --fail --silent --show-error --max-time 10 "${BASE_URL}/" -o "$TEMP_DIR/index.html"
grep -Eiq '<html([[:space:]>])' "$TEMP_DIR/index.html" || {
  echo "ERRO: frontend não foi servido na rota /" >&2; exit 1;
}
echo "OK · Angular servido pela aplicação"

[[ "$(http_status "${BASE_URL}/api/catalog")" == 200 ]] || {
  echo "ERRO: catálogo público indisponível" >&2; exit 1;
}
echo "OK · Catálogo público"

curl --silent --show-error --max-time 10 -D "$TEMP_DIR/csrf.headers" -o /dev/null "${BASE_URL}/api/auth/csrf"
grep -Eiq '^set-cookie:[[:space:]]*XSRF-TOKEN=' "$TEMP_DIR/csrf.headers" || {
  echo "ERRO: cookie XSRF-TOKEN não foi emitido" >&2; exit 1;
}
echo "OK · CSRF"

ADMIN_STATUS=$(http_status "${BASE_URL}/api/admin/products")
[[ "$ADMIN_STATUS" == 401 || "$ADMIN_STATUS" == 403 ]] || {
  echo "ERRO: API administrativa respondeu HTTP $ADMIN_STATUS sem autenticação" >&2; exit 1;
}
echo "OK · API administrativa protegida ($ADMIN_STATUS)"

if [[ -n "$MAILPIT_URL" ]]; then
  [[ "$(http_status "${MAILPIT_URL%/}/api/v1/messages")" == 200 ]] || {
    echo "ERRO: Mailpit indisponível" >&2; exit 1;
  }
  echo "OK · Mailpit local"
fi
echo "Smoke test concluído. Nenhum dado comercial foi alterado."
