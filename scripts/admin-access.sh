#!/usr/bin/env bash
# Exibe no terminal privado as credenciais de bootstrap sem alterar usuários ou banco.
set -euo pipefail
cd "$(dirname "$0")/.."
[[ -f .env ]] || { echo "Arquivo .env não encontrado. Execute bash scripts/start.sh." >&2; exit 1; }
[[ -t 1 ]] || { echo "Use diretamente o terminal interativo privado do Codespace." >&2; exit 1; }
python3 - <<'PY'
from pathlib import Path
settings = {}
for raw in Path(".env").read_text(encoding="utf-8").splitlines():
    line = raw.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    key, value = line.split("=", 1)
    settings[key.strip()] = value.strip().strip("'\"")
print("Loja · administrador inicial")
print("Usuário:", settings.get("BOOTSTRAP_ADMIN_USERNAME", "admin"))
print("Senha inicial do .env:", settings.get("BOOTSTRAP_ADMIN_PASSWORD", "[não definida]"))
print("Atenção: essa senha só funciona se não tiver sido alterada depois da criação do dono.")
print("Não compartilhe esta saída ou o arquivo .env.")
PY
