# Hospedagem Railway (Java 21 + Angular + Supabase)

## Por quê

A Loja roda Spring Boot 4.1.2 com Angular servido pelo mesmo Java. Em Vercel Container Functions a aplicação inicia em torno de 18–21 segundos por instância e novas instâncias abriram conexões demais no Supabase: erro `EMAXCONNSESSION` no pool Session de 15 clientes. O serviço persistente Railway evita reiniciar a JVM a cada solicitação. **Não há migração de dados**: o PostgreSQL atual continua no Supabase.

**Não desative o site Vercel nem mude o link oficial enquanto a Railway não estiver autenticada e testada.** A migração pode gerar custos de hospedagem. Confira plano e limites antes de publicar.

## Serviço único

1. Railway → New Project → Deploy from GitHub Repo → `WalterMatheusgg/SistemaMercearia`, branch `main`.
2. Deixe Root Directory na raiz (`/`); o `Dockerfile` multiestágio da raiz é detectado automaticamente. Ele compila Angular e o Spring Boot e inicia o servidor via `java -jar`.
3. Defina **uma única réplica**, sem Serverless/App Sleeping. Configure healthcheck `/actuator/health` (timeout inicial de 120 s). O projeto não precisa de um segundo banco Railway.
4. Escolha região próxima de `us-east-1` para reduzir latência até o Supabase.
5. Na aba **Variables**, configure os valores a seguir diretamente no painel Railway. **Não envie senhas pelo chat, Issues ou commits**:

| Nome | Valor |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod,railway` |
| `DB_URL` | `jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:5432/postgres?sslmode=require&currentSchema=loja&connectTimeout=10` |
| `DB_USERNAME` | `loja_app.kppoisedwqxagbpucxwv` |
| `DB_PASSWORD` | senha privada do papel `loja_app` (já cadastrada na Vercel; adicione pela interface protegida da Railway) |
| `SESSION_COOKIE_SECURE` | `true` |
| `APP_JOBS_ENABLED` | `false` enquanto não houver SMTP homologado |
| `BOOTSTRAP_ADMIN_ENABLED` | `false` |
| `APP_PUBLIC_URL` | `https://<domínio-gerado-pela-Railway>` após gerar domínio |

O `PORT` é atribuído pela Railway e lido pelo perfil `railway`; não é necessário configurá-lo manualmente.

**Confirme no botão Connect do projeto Supabase** que o endereço `aws-0-us-east-1.pooler.supabase.com` e o usuário do Session pooler continuam válidos. Evite Direct Connection IPv6 em hospedagem IPv4. Nunca use a URL do banco Licits.

6. Deploy → gerar domínio Railway HTTPS → abrir `/actuator/health`, `/login` e testar login real com `admin` e senha privada existente. O bootstrap da conta **não** deve ser repetido; `admin` já está no banco.
7. Validar com usuários reais: permissões, sessão/CSRF, lista de produtos, foto, busca, cadastro, estoque, venda e histórico. Faça vendas somente com dados de teste identificados e avalie a remoção segura posterior; nunca apague registros contábeis para limpar testes.
8. Compare tempos de resposta antes/depois com navegador e logs. Confirme ausência de 500, `EMAXCONNSESSION`, reinicializações e latência alta na API.
9. **Só depois de aprovada**: planejar troca do endereço público e `APP_PUBLIC_URL`. O endereço `loja-ferragens.vercel.app` pertence à Vercel e não deve ser anunciado como Railway sem configurar DNS/redirect.

## Notificações e manutenção

- A fila `notification_outbox` já está no Supabase; as preferências do dono já foram habilitadas no cadastro.
- `APP_JOBS_ENABLED=false` evita tarefa periódica sem SMTP funcional. Para habilitar: configurar e testar `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_AUTH`, `MAIL_STARTTLS` e `MAIL_SSL` com remetente verificado. Confirmar o e-mail do administrador no próprio sistema.
- O agendador deve operar em **uma única instância** (sem duplicações de e-mails e expiração de reservas).
- A Railway oferece execução persistente, mas exige plano/cobrança e monitoramento adequados. Configure disponibilidade, orçamento, backups do Supabase fora do provedor e teste de restauração.

## Situação

Este guia e o perfil `application-railway.yml` tornam o repositório compatível com o deploy persistente. **Ter o plugin Railway conectado não configura automaticamente a conta, as variáveis nem o domínio.** Não declare migração concluída até o healthcheck e os testes de navegação passarem na nova URL.
