# Infraestrutura

- `compose.yaml` na raiz: PostgreSQL + Mailpit no Codespace; dados no volume local.
- `infra/compose.production.yaml`: monólito Spring/Angular e PostgreSQL persistente, app somente no loopback.
- `infra/compose.https.yaml` + `infra/Caddyfile`: Caddy com TLS automático, domínios configurados por variável de ambiente e proxy da mesma origem.
- `scripts/backup.sh` e `scripts/restore-test.sh`: por padrão banco local; em produção use `COMPOSE_CONFIG=infra/compose.production.yaml`.

A configuração pública exige domínio, VM com Docker, .env privado e SMTP legítimo. Não há recursos cloud provisionados por esses arquivos. Antes de iniciar uma loja real, siga a implantação e a homologação em [docs/HOSPEDAGEM.md](../docs/HOSPEDAGEM.md).

**Atenção:** não exponha PostgreSQL, SMTP local ou porta 8080 à Internet; não apague volumes para resolver erros de container.
