# Homologação Vercel + Supabase

A Loja é uma aplicação monolítica Java 21 (Spring Boot) que serve a interface Angular e a API na mesma origem. O `Dockerfile` da raiz permite build como **Vercel Container Function**.

## Como está organizado

- Vercel: projeto `loja-ferragens` (código do repositório `SistemaMercearia`).
- Supabase: projeto separado `Loja Ferragens`, PostgreSQL. As tabelas são criadas pelo Flyway em um **esquema privado** `loja`, não exposto à Data API.
- Sessões de login: persistidas em tabelas Spring Session JDBC no banco, porque as funções da Vercel não são servidores permanentes.
- Tarefas recorrentes e envio de notificações: **desativados** no perfil `vercel`. Não prometa relatórios por e-mail agendados nessa implantação.

## Configuração segura de homologação

Na Vercel, as variáveis privadas `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` devem apontar para o PostgreSQL da Loja; nunca para Licits. Use JDBC com SSL e `currentSchema=loja`. Em hospedagem IPv4-only, obtenha o **hostname exato** do *Session pooler* no botão **Connect** do Supabase: não invente o índice `aws-0`/`aws-1`. O usuário do pooler para role `loja_app` é `loja_app.<project-ref>`.

Defina ainda `SPRING_PROFILES_ACTIVE=prod,vercel`, `APP_PUBLIC_URL` com HTTPS, `SESSION_COOKIE_SECURE=true`, `PORT=8080` e `BOOTSTRAP_ADMIN_ENABLED=false` depois do primeiro administrador criado. Somente um banco vazio precisa temporariamente de `prod,vercel,bootstrap` e de credenciais fortes de bootstrap. Remova-as depois do primeiro acesso. O perfil `prod` obriga cookies seguros e desabilita Swagger público.

Verifique o banco (`flyway_schema_history` versões V1–V10 em `loja`), `/actuator/health`, login e ações não destrutivas de Produtos e Estoque. A atualização não migra automaticamente dados do Codespace; consulte o procedimento de backup e restauração em `docs/HOSPEDAGEM.md`.

## Limites: não é produção de loja comercial

Vercel Hobby é apenas pessoal/não comercial. Container Functions são efêmeras; o servidor pode parar após inatividade. Os trabalhos agendados e e-mail da Loja não funcionam nessa implantação sem um worker persistente e SMTP real. Para operação comercial, é necessário plano/host compatível, monitoramento, testes operacionais e backups externos. Use o Compose com PostgreSQL e HTTPS no servidor persistente conforme `docs/HOSPEDAGEM.md`.
