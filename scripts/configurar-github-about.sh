#!/usr/bin/env bash
# Configura somente a descrição, homepage e tópicos do About.
set -Eeuo pipefail
REPO="WalterMatheusgg/SistemaMercearia"
DESCRIPTION="🔩 Gestão de loja de ferragens com Java 21, Spring Boot 4, Angular 22 e PostgreSQL. Estoque, vendas, compras, caixa e relatórios."
HOMEPAGE="https://loja-ferragens.vercel.app"
TOPICS="java,spring-boot,angular,typescript,postgresql,docker,fullstack,gestao-de-estoque,point-of-sale,store-management"

if [[ "$#" -eq 1 && "$1" == "--help" ]]; then
  echo "Uso: bash scripts/configurar-github-about.sh"
  echo "Requer 'gh' autenticado com permissão de administrador."
  exit 0
fi
[[ "$#" -eq 0 ]] || { echo "Argumentos inválidos. Use --help." >&2; exit 1; }
command -v gh >/dev/null 2>&1 || { echo "Instale o GitHub CLI: https://cli.github.com/" >&2; exit 1; }
gh auth status >/dev/null || { echo "Autentique no terminal privado com: gh auth login" >&2; exit 1; }
owner_repo="$(gh repo view "$REPO" --json nameWithOwner --jq '.nameWithOwner')"
[[ "$owner_repo" == "$REPO" ]] || { echo "Repositório inesperado. Nenhuma alteração feita." >&2; exit 1; }

gh repo edit "$REPO" \
  --description "$DESCRIPTION" \
  --homepage "$HOMEPAGE" \
  --add-topic "$TOPICS"

echo "About atualizado. Campos:"
gh repo view "$REPO" --json nameWithOwner,description,homepageUrl \
  --jq '{repositorio: .nameWithOwner, descricao: .description, site: .homepageUrl}'
gh api "repos/$REPO/topics" --jq '.names'
