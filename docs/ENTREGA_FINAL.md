# Entrega da Loja — Sistema de gestão e vendas

**Fonte canônica:** [main do SistemaMercearia](https://github.com/WalterMatheusgg/SistemaMercearia/tree/main). A antiga branch de trabalho foi integrada e excluída. Use a versão da `main` para atualizações e instalações.

A marca do aplicativo é apenas **Loja**, com títulos individualizados por seção no navegador. [Veja os ajustes visuais](DESIGN_LOJA.md).

Esta entrega prioriza o **controle interno**. A vitrine pública e o cadastro externo estão desativados por padrão; nenhum registro comercial é apagado. Produtos não exigem código manual. Senhas novas seguem a regra de 4 caracteres e 1 número.

## O que está entregue

- Java 21 / Spring Boot 4 / Angular 22 / PostgreSQL 18; sessão e CSRF no mesmo domínio.
- Gestão de usuários, produtos, inventário, fornecedores, vendas e orçamentos, recebimentos, caixa, financeiro, clientes, loja online e notificações.
- Invariantes testadas: reserva de estoque, idempotência, permissões, pagamentos registrados manualmente, auditoria e precisão decimal.
- Docker local funcionando no Codespace; scripts de backup/restauração, Compose de produção com Caddy/HTTPS e validação de configuração.
- CI com testes de integração, Angular, navegador desktop/mobile, restore de backup e smoke test sem alteração de dados.

## Verificações seguras no Codespace

```bash
cd /workspaces/SistemaMercearia
git switch main
git pull --ff-only origin main
bash scripts/start.sh
bash scripts/smoke.sh
docker compose --profile app ps
```

**Nenhum dos comandos acima apaga os volumes.** O teste `smoke.sh` não cadastra produtos ou lança vendas. O acesso do navegador é pela porta 8080; Mailpit local pela 8025.

## Entrada em produção

Execute `python3 scripts/preflight-production.py .env` na VM antes do primeiro deploy. Configure domínio e DNS, servidor Linux, backups externos, credenciais de PostgreSQL e SMTP reais, política de segurança e homologação. Siga [HOSPEDAGEM.md](HOSPEDAGEM.md). O Codespace é ambiente de desenvolvimento; **ainda não existe URL pública homologada**.

No banco novo, `SPRING_PROFILES_ACTIVE=prod,bootstrap` e `BOOTSTRAP_ADMIN_ENABLED=true` são temporários. Após entrar como dono, use somente o perfil `prod` e desative o bootstrap. Ao migrar um banco já existente, evite inicializar/reescrever dados; restaure o backup de forma planejada.

A aplicação **não** integra cobrança bancária Pix/cartão, emissão de nota fiscal ou transportadora. Os pagamentos são conferidos e registrados manualmente. Para uma loja real, integrar esses serviços requer provedores e homologação próprios; não trate essas funcionalidades como prontas.

## Critério de aceite

- [x] Projeto completo na `main`, sem dependência da branch apagada.
- [x] Inicialização Docker local demonstrada com PostgreSQL saudável e HTTP 200.
- [x] Testes automatizados de módulos e navegador no GitHub Actions antes da integração.
- [ ] Testes manuais pelo responsável na loja com os dados reais.
- [ ] VM, DNS, HTTPS e entrega SMTP externo configurados e homologados.
- [ ] Backups criptografados fora da VM, monitoração e procedimento de recuperação operacionais.

Não considerar a loja **em produção** até concluir os itens pendentes.
