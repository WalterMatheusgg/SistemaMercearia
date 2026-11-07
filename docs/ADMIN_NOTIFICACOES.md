# Primeiro administrador e alertas por e-mail — Loja Ferragens

O ambiente Vercel `loja-ferragens` usa Spring Boot e Supabase no schema privado `loja`.

## Estado da produção em 07/11/2026

O primeiro administrador já foi criado e verificado como `ROLE_OWNER` no banco Supabase. O bootstrap foi desativado no ambiente. **Não reative o bootstrap nem substitua a senha existente para atualizar o README ou o Codespace.** O e-mail do administrador estava pendente de verificação e as notificações externas permanecem sem SMTP e worker homologados.

A seção seguinte é um procedimento de recuperação/instalação para **banco vazio**, não uma tarefa pendente do ambiente atual.

## Criação inicial do dono (somente uma vez)

O bootstrap **não funciona sem senha inicial**, porque a aplicação não deve criar um administrador com uma senha conhecida ou publicada. Em **Vercel → loja-ferragens → Settings → Environment Variables**, configure estas variáveis em Production:

- `BOOTSTRAP_ADMIN_USERNAME=admin` (pré-configurado)
- `BOOTSTRAP_ADMIN_EMAIL=waltermatheus688@gmail.com` (pré-configurado, precisa confirmar posse)
- `BOOTSTRAP_ADMIN_PASSWORD`: senha longa e aleatória, configurada como **Sensitive**, sem compartilhá-la no ChatGPT/GitHub
- `SPRING_PROFILES_ACTIVE=prod,vercel,bootstrap`
- `BOOTSTRAP_ADMIN_ENABLED=true`

Faça um redeploy do `main` e confirme em Supabase SQL Editor: `SELECT username,email,email_verified FROM loja.app_user;`. O bootstrap ocorre apenas quando não há dono ativo. O email fica **não verificado** e as preferências de alertas são marcadas, mas não serão enviadas até a verificação e SMTP.

Depois de entrar com o usuário `admin`, altere a senha no sistema. Por fim: `BOOTSTRAP_ADMIN_ENABLED=false`, `SPRING_PROFILES_ACTIVE=prod,vercel`, **remova** `BOOTSTRAP_ADMIN_PASSWORD` e faça um novo redeploy.

Não revele a senha na conversa, não a coloque em commits e não use passwords comuns ou padrão.

## Notificações: o que já existe

A aplicação inclui fila PostgreSQL (`notification_outbox`) com tentativas e reprocessamento, preferências por usuário e eventos `SALE_COMPLETED`, `STOCK_LOW`, `DAILY_SUMMARY`. Na criação do administrador com e-mail, o código cadastra suas três preferências habilitadas (sem ativar envio antes da confirmação).

Em produção, a aplicação só envia para usuários ativos, com e-mail confirmado e preferências habilitadas.

### Por que o envio externo não deve ser ativado por enquanto

A Vercel hospeda Container Functions efêmeras, não um worker permanentemente ativo; com `APP_JOBS_ENABLED=false`, tarefas `OutboxWorker` e `ReservationExpiry` não rodam. Alterar para `true` sem worker persistente **não resolve** nem garante periodicidade.

Além disso, é necessário contratar ou configurar um provedor SMTP válido e credenciais privadas `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_AUTH`, `MAIL_STARTTLS`, `MAIL_FROM`. Não use um endereço de remetente não verificado. Não há provedor SMTP conectado no projeto até esta revisão.

Para entrega confiável, hospede um worker persistente ou um agendamento externo com autenticação e monitore a fila. Use o SMTP real para enviar primeiro a confirmação do e-mail do administrador, depois valide eventos de venda/estoque e resumo com cenários controlados.

## Verificação

```sql
SELECT username, email, email_verified, enabled FROM loja.app_user;
SELECT sale_completed, stock_low, daily_summary FROM loja.notification_preference;
SELECT status, count(*) FROM loja.notification_outbox GROUP BY status;
```

A implantação não deve ser considerada pronta para alertas por e-mail antes de uma mensagem real recebida e registrada em `SENT`.
