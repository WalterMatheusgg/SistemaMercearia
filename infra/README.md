# 🛠️ Infraestrutura — Loja Ferragens

Configurações de execução para o monólito **Spring Boot + Angular**, serviços PostgreSQL e HTTPS.

| Arquivo | Finalidade |
|:--|:--|
| `../compose.yaml` | Desenvolvimento no Codespaces: PostgreSQL, Mailpit e aplicação |
| `compose.production.yaml` | Aplicação e PostgreSQL persistentes para servidor Linux |
| `compose.https.yaml` + `Caddyfile` | Caddy com HTTPS e proxy reverso na mesma origem |
| `../Dockerfile` | Build multiestágio Angular + backend Java 21 |

O banco local usa volume persistente. Os scripts `../scripts/backup.sh` e `../scripts/restore-test.sh` ajudam a validar recuperação, mas os dumps de operação real precisam ser copiados para armazenamento externo seguro.

## 🌐 Implantação atual

- **Vercel + Supabase:** disponível para homologação, porém com cold starts e limitação de conexões; não aprovado para operação comercial.
- **Railway:** perfil `prod,railway` preparado em `../backend/src/main/resources/application-railway.yml`; o serviço persistente ainda não está implantado e validado.
- **Servidor Docker:** Compose de produção com Caddy/HTTPS disponível, mas a VM e o DNS precisam ser configurados.

## 🔒 Cuidados

Nunca exponha PostgreSQL, SMTP local ou porta 8080 diretamente à Internet. Mantenha segredos apenas em variáveis privadas; não versione `.env`, não execute `docker compose down -v` e não restaure banco em operação sem testar previamente.

[Hospedagem comercial](../docs/HOSPEDAGEM.md) · [Railway](../docs/RAILWAY.md) · [README principal](../README.md)
