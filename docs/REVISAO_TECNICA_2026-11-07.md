# Revisão técnica da Loja — 07/11/2026

**Origem:** `WalterMatheusgg/SistemaMercearia`. Esta revisão cobre o repositório, CI, Vercel e Supabase. Não houve acesso direto ao terminal/volumes do Codespace nem homologação interativa das telas em produção.

## Estado confirmado

- Antes da revisão: `main=ce557bc`; GitHub Actions `42330548310` aprovou backend, frontend e testes E2E.
- Vercel: deploy `dpl_BjfPWynZdktxJFW32bp4A3NoXF8F` READY e logs com `Started FerragensApplication`; a compilação sozinha não garante disponibilidade contínua.
- Supabase: PostgreSQL 17, schema privado `loja`, 35 tabelas e 10 migrações (Flyway V10). Papéis `anon`/`authenticated` sem USAGE em `loja`.
- Banco de produção tinha **0 usuários, 0 donos (ROLE_OWNER), 0 produtos, 0 vendas**. Login administrativo ainda não homologado.
- Codespace: não há ferramenta conectada que confirme o HEAD local ou permita executar comandos na máquina. O script precisa ser rodado no Codespace.

## Achados prioritários

| Risco | Evidência | Ação |
| --- | --- | --- |
| P0 — Administrador ausente | `app_user=0`, `ROLE_OWNER=0` | Bootstrap temporário com segredo privado; depois remover bootstrap e redeploy |
| P0 — Hospedagem comercial | Vercel Hobby da conta conectada | Usar hospedagem/plano autorizado para negócio real |
| P0 — Dados locais | Supabase não recebeu dados do Codespace | Backup e migração planejada, nunca restaurar por cima de dados sem verificar |
| P1 — Jobs desligados | Perfil Vercel usa `APP_JOBS_ENABLED=false` | Agendar worker persistente para emails, resumo e expiração de reservas |
| P1 — SMTP não homologado | Sem prova de servidor SMTP de produção | Configurar remetente e testar entrega/retentativas sem dados reais |
| P1 — Limite de login local à instância | `AuthRateLimitFilter` guarda contadores em memória | Aplicar limitação distribuída/WAF no ambiente externo |
| P1 — Testes operacionais | CI usa ambiente Docker isolado, não a produção | Testar login, sessões, CSRF, vendas, estoque, compras, financeiro e fotos |
| P1 — Backup externo ausente | Scripts geram dumps dentro do projeto ignorados pelo Git | Backup cifrado fora da VM/Codespace e ensaio de restauração |
| P1 — Proteção SSO Vercel | SSO ativo em todos os deploys | Rever publicação apenas depois da homologação |
| P2 — Função SQL search_path | Aviso Supabase `function_search_path_mutable` | Nova V11 fixa `pg_catalog` sem alterar dados |
| P2 — Índices | Advisor citou 18 chaves estrangeiras sem índice | Medir consultas e carga antes de adicionar índices |

## Áreas verificadas

Backend Java/Spring Security: papéis, CSRF, sessões JDBC, cadastro de usuários, validação por revisão e rate limit. Banco: Flyway e triggers de histórico, produtos, inventário, vendas, compras, financeiro e sessões. Interface Angular: rotas e presença de testes unitários/E2E desktop/mobile. Infraestrutura: Dockerfile, Compose, scripts de backup, restauração e smoke test, perfis local/prod/vercel e Vercel/Supabase.

## Sincronização do Codespace

Primeiro salve alterações locais (o script interrompe se houver trabalho não commitado). Em Codespace já configurado:

```bash
cd /workspaces/SistemaMercearia
git status --short
bash scripts/backup.sh
git switch main
git pull --ff-only origin main
bash scripts/sync-codespace.sh
```

O pull inicial instala o script, sem executar migrações. Se ainda não existir `.env`, pule somente o backup inicial. O script faz backup de novo, impede sobrescrita de mudanças, atualiza por fast-forward, testa e inicia com volumes preservados. Não publica commits locais automaticamente.

## Pendente para a homologação

- [ ] Executar script de sincronização dentro do Codespace e guardar dump externamente.
- [ ] CI deste commit aprovado; migração V11 aplicada e Advisor reavaliado.
- [ ] Criar administrador remoto por bootstrap seguro e **desativar** bootstrap depois.
- [ ] Testes autenticados de produtos, movimentações, vendas, devoluções, compras, caixa e financeiro.
- [ ] Operação comercial em hospedagem compatível com worker permanente, SMTP, monitoramento e backup externo.

**Resultado:** revisão técnica com mudanças conservadoras; a Loja não deve ser descrita como 100% pronta para comércio até completar os critérios pendentes.
