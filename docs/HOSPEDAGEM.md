# Hospedagem do sistema comercial — arquitetura preservada

Este procedimento mantém o monólito Spring Boot/Angular e o PostgreSQL existentes. O JAR serve o frontend e a API na **mesma origem HTTPS**; não há alterações nos cookies, CSRF ou sessões.

## Decisão de hospedagem (verificada em 04/11/2026)

- **Vercel Hobby:** plano gratuito restringido a uso pessoal não comercial. Não publique uma loja em operação real neste plano. Os runtimes oficiais de Functions não incluem servidor Java 21 persistente nem Docker Compose. Vercel Pro permite uso comercial, mas não foi autorizada contratação.
  - https://vercel.com/docs/plans/hobby
  - https://vercel.com/docs/functions/runtimes
- **Render Free:** serviço pode hibernar após 15 minutos sem tráfego. Isso interrompe expiração de reservas, outbox e resumo das 21h. Seu PostgreSQL gratuito expira em 30 dias; não deve ser o único banco de uma loja em funcionamento.
  - https://render.com/docs/free
- **Oracle Cloud Always Free (opção técnica sob disponibilidade):** VM Linux ARM Ampere A1 permite executar o Docker Compose continuamente dentro da cota gratuita, sem mudar a arquitetura. A capacidade e a elegibilidade precisam ser verificadas na conta. Instâncias consideradas ociosas podem ser recuperadas pelo provedor: não há garantia de produção. Faça backup **fora** da máquina.
  - https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm

**Não foi provisionada uma VM e não há URL externa confirmada.** A criação de conta, a escolha da região, o DNS e a verificação de limites são etapas externas necessárias. Não utilize o Codespace como servidor definitivo.

## Alternativa persistente na Railway

O projeto possui um perfil `prod,railway` e instruções em [RAILWAY.md](RAILWAY.md). O servidor persistente mantém o monólito Java/Angular na mesma origem, usando o banco já existente no Supabase. É uma opção de hospedagem com possíveis custos; exige configurar as variáveis privadas pelo provedor e testar antes de transferir acessos.

## Implantação Docker com HTTPS (VM Linux ARM64 ou AMD64)

1. Provisione uma VM com armazenamento persistente e capacidade adequada, instale Docker Engine + Compose v2 e restrinja firewall. Permita entrada TCP 80 e 443 para Caddy; SSH apenas a administradores. **Não publique portas 5432, 8025 ou 8080**.
2. Configure o domínio com DNS A/AAAA para o IP público da VM. HTTPS automático exige um nome válido alcançável pelas autoridades certificadoras.
3. Na VM, obtenha a **`main` definitiva**:

   ```bash
   git clone --branch main --single-branch https://github.com/WalterMatheusgg/SistemaMercearia.git
   cd SistemaMercearia
   cp .env.example .env
   chmod 600 .env
   ```

4. Edite `.env` **somente na VM**, sem enviar valores para chat, Git ou logs:

   | Variável | Configuração obrigatória |
   | --- | --- |
   | `APP_DOMAIN` | domínio real da loja, sem `https://` |
   | `APP_PUBLIC_URL` | `https://` + domínio, consistente com APP_DOMAIN |
   | `SESSION_COOKIE_SECURE` | `true` |
   | `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | banco e senha fortes, mantidos no volume persistente |
   | `BOOTSTRAP_ADMIN_ENABLED` | `true` **somente no primeiro início de um banco vazio**; depois desativar |
   | `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_PASSWORD` | credenciais iniciais privadas e fortes; não redefinem contas existentes |
   | `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM` | SMTP real e remetente verificado |
   | `MAIL_USERNAME`, `MAIL_PASSWORD` | credenciais SMTP privadas |
   | `MAIL_AUTH`, `MAIL_STARTTLS`, `MAIL_SSL` | compatíveis com o provedor, normalmente `true`, `true`, `false` |

   **Primeiro administrador em banco novo:** além de `BOOTSTRAP_ADMIN_ENABLED=true`, defina temporariamente `SPRING_PROFILES_ACTIVE=prod,bootstrap` no `.env` antes da primeira execução. Só o perfil `prod` não executa a criação do dono. Depois do primeiro login, configure `SPRING_PROFILES_ACTIVE=prod` e `BOOTSTRAP_ADMIN_ENABLED=false`, remova o segredo de bootstrap e recrie o contêiner `app`. Se você estiver restaurando um banco já existente, **não** use o perfil `bootstrap`.

   Antes de qualquer `up` em produção, verifique as variáveis sem expor senhas:

   ```bash
   python3 scripts/preflight-production.py .env
   ```

   O `infra/compose.https.yaml` define `APP_PUBLIC_URL` como HTTPS e habilita os jobs. O perfil `prod` força cookie Secure e desabilita a documentação de API pública. Para Brevo SMTP, use o servidor `smtp-relay.brevo.com` e a **chave SMTP**, não a chave da API; confirme elegibilidade e limites do plano gratuito no provedor (300 mensagens/dia segundo a documentação consultada).

5. Se o banco é **novo**, inicialize o conjunto após revisar os segredos:

   ```bash
   docker compose --env-file .env -f infra/compose.production.yaml -f infra/compose.https.yaml config --quiet
   docker compose --env-file .env -f infra/compose.production.yaml -f infra/compose.https.yaml up -d --build
   docker compose --env-file .env -f infra/compose.production.yaml -f infra/compose.https.yaml ps
   ```

   Após criação do administrador, defina `BOOTSTRAP_ADMIN_ENABLED=false`, `SPRING_PROFILES_ACTIVE=prod`, **retire `BOOTSTRAP_ADMIN_PASSWORD` do `.env`** e recrie somente o app para retirar o bootstrap das configurações ativas:

   ```bash
   docker compose --env-file .env -f infra/compose.production.yaml -f infra/compose.https.yaml up -d --force-recreate app
   ```

6. **Se já existe banco com dados do Codespace**, não inicie o app sobre um banco vazio e depois restaure por cima. Primeiro gere um dump no Codespace com `bash scripts/backup.sh`, copie-o à VM por transporte privado, inicie **somente** `postgres` com o Compose de produção e restaure no banco novo antes de subir o app e o proxy:

   ```bash
   docker compose --env-file .env -f infra/compose.production.yaml up -d postgres
   docker compose --env-file .env -f infra/compose.production.yaml exec -T postgres pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --exit-on-error --no-owner < backups/backup-transferido.dump
   docker compose --env-file .env -f infra/compose.production.yaml -f infra/compose.https.yaml up -d --build
   ```

   Antes de iniciar o app com dados restaurados, confirme `SPRING_PROFILES_ACTIVE=prod` e `BOOTSTRAP_ADMIN_ENABLED=false`. As variáveis `POSTGRES_USER`/`POSTGRES_DB` do último bloco precisam estar disponíveis no shell atual (por exemplo `set -a; . ./.env; set +a`). **Não execute a restauração em um banco já inicializado por Flyway** sem elaborar e ensaiar um plano de migração. Execute e confira uma restauração isolada antes de transferir dados reais.

## Backups e operação

Os scripts originais agora aceitam `COMPOSE_CONFIG`; o padrão continua sendo `compose.yaml` do Codespace. Na VM de produção:

```bash
COMPOSE_CONFIG=infra/compose.production.yaml bash scripts/backup.sh
COMPOSE_CONFIG=infra/compose.production.yaml bash scripts/restore-test.sh backups/ARQUIVO.dump ferragens_restore_teste
docker compose --env-file .env -f infra/compose.production.yaml -f infra/compose.https.yaml logs --tail=150 app
curl -fsS https://SEU-DOMINIO/actuator/health
```

Guarde os dumps criptografados **fora** da VM (outro local/armazenamento com controle de acesso), retenção e testes regulares de restauração. Nunca execute `docker compose down -v`: isso apaga volumes, inclusive PostgreSQL e certificados.

Os jobs Spring (expiração a cada minuto, outbox e resumo às 21h de Brasília) exigem o app ativo. Há risco residual de manutenção, reinício, indisponibilidade da VM e duplicação SMTP em falhas de rede; configure monitoramento e validação de alertas. Não configure uma segunda instância do app sem avaliar sessões compartilhadas e serialização de jobs.

## Homologação indispensável

- HTTPS válido, redirecionamento HTTP → HTTPS, cookie `Secure; HttpOnly; SameSite=Lax`, CSRF e sessão nos formulários; abertura de link de recuperação pelo domínio público.
- Permissão de cliente vs. equipe, conta desativada, logs sem dados sensíveis; validar no navegador desktop/celular.
- Estoque: 50 − 15 = 35; entrada +20 = 55; pedido pendente reserva e expiração libera; concorrência/idempotência.
- Abertura/fechamento de caixa com dinheiro entregue e troco líquido; pagamentos mistos; compra recebida parcialmente e contas a receber/a pagar.
- SMTP externo: verificação, recuperação, uma notificação de venda e um resumo real; conferir filas e falhas.
- Backup **e** restauração em banco isolado; monitoramento de disponibilidade e espaço do PostgreSQL.
- Emissão fiscal e recebimento Pix/cartão **não** são automáticos; pagamentos precisam ser conferidos fora do site.

Somente depois dessas verificações, com dados reais, operador responsável e procedimento de contingência, considerar a abertura ao público.
