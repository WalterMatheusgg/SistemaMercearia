#!/usr/bin/env bash
# GitHub main -> Codespace; não descarta dados, commits ou arquivos locais.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
msg() { printf '[Loja] %s\n' "$*"; }
fail() { printf '[Loja] ERRO: %s\n' "$*" >&2; exit 1; }

if [[ "$#" -eq 1 && "$1" == "--help" ]]; then
  echo "Uso: bash scripts/sync-codespace.sh"
  echo "Backup local (se houver .env), atualização fast-forward, testes, Docker e smoke não destrutivo."
  exit 0
fi
[[ "$#" -eq 0 ]] || fail "Argumentos inválidos; consulte --help."
for cmd in git docker curl python3; do
  command -v "$cmd" >/dev/null 2>&1 || fail "Instale o comando necessário: $cmd"
done
[[ "$(git rev-parse --show-toplevel 2>/dev/null)" == "$ROOT" ]] || fail "Abra o repositório SistemaMercearia."
remote="$(git remote get-url origin 2>/dev/null || true)"
case "$remote" in
 https://github.com/WalterMatheusgg/SistemaMercearia|https://github.com/WalterMatheusgg/SistemaMercearia.git|git@github.com:WalterMatheusgg/SistemaMercearia|git@github.com:WalterMatheusgg/SistemaMercearia.git) ;;
 *) fail "Remoto origin inesperado; confira git remote -v." ;;
esac
branch="$(git symbolic-ref --short -q HEAD || true)"
[[ "$branch" == main ]] || fail "Primeiro mude manualmente para a branch main, sem perder mudanças."
[[ -z "$(git status --porcelain --untracked-files=normal)" ]] || fail "Há alterações locais: faça commit ou stash manual. Nada foi modificado."

if [[ -f .env ]]; then
  msg "Fazendo backup local antes de atualizar..."
  backup="$(bash scripts/backup.sh)" || fail "Backup falhou; atualização interrompida."
  [[ -s "$backup" ]] || fail "Backup não validado; nada foi atualizado."
  msg "Backup preservado em $backup. Guarde cópia externa também."
else
  msg "Não há .env local; ambiente novo ainda sem banco configurado."
fi
git fetch --no-tags origin main
local_head="$(git rev-parse HEAD)"
remote_head="$(git rev-parse refs/remotes/origin/main)"
git merge-base --is-ancestor "$local_head" "$remote_head" || fail "Branch main divergente ou com commits locais. Não será feito reset."
git merge --ff-only "$remote_head"
msg "Commit local: $(git rev-parse --short HEAD)"
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
bash scripts/start.sh
bash scripts/smoke.sh
docker compose --env-file .env -f compose.yaml --profile app ps
msg "Sincronização e smoke test concluídos. Abra a porta 8080 do Codespace."
