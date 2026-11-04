#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$TASK_ROOT"
command -v docker >/dev/null || { echo 'Docker não encontrado. Inicie em um Codespace com Docker.' >&2; exit 1; }
if [[ ! -f .env ]]; then
  # New local environment only. Existing credentials and databases are never overwritten.
  umask 077
  python3 - <<'PY'
import os,secrets,pathlib
text=pathlib.Path('.env.example').read_text()
db=secrets.token_urlsafe(30)
text=text.replace('local-only-change-this',db).replace('replace-with-a-long-local-password',secrets.token_urlsafe(24))
if os.getenv('CODESPACE_NAME') and os.getenv('GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN'):
 text=text.replace('APP_PUBLIC_URL=http://localhost:8080','APP_PUBLIC_URL=https://'+os.environ['CODESPACE_NAME']+'-8080.'+os.environ['GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN'])
pathlib.Path('.env').write_text(text)
PY
  echo 'Arquivo .env criado com senhas locais aleatórias. Consulte nele o usuário/senha do administrador.'
fi
python3 - <<'PYENV'
import os,pathlib,re
path=pathlib.Path('.env');text=path.read_text()
if not re.search(r'^APP_PUBLIC_URL=',text,re.M):
 url='http://localhost:8080'
 if os.getenv('CODESPACE_NAME') and os.getenv('GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN'):
  url='https://'+os.environ['CODESPACE_NAME']+'-8080.'+os.environ['GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN']
 path.write_text(text+'\nAPP_PUBLIC_URL='+url+'\n')
PYENV
chmod 600 .env
set -a
. "$TASK_ROOT/.env"
set +a
TASK_PORT=${APP_PORT:-8080}
docker compose --env-file .env --profile app up -d --build
for attempt in $(seq 1 60); do
  if curl --fail --silent "http://localhost:$TASK_PORT/actuator/health" >/dev/null; then
    echo "Aplicação pronta. Abra a porta $TASK_PORT no Codespace. Mailpit: porta 8025."
    exit 0
  fi
  sleep 2
done
echo 'A aplicação ainda não respondeu. Confira: docker compose --profile app logs --tail=100 app' >&2
exit 1
