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

Verifique o banco (`flyway_schema_history` versões V1–V11 em `loja`), `/actuator/health`, login e ações não destrutivas de Produtos e Estoque. A atualização não migra automaticamente dados do Codespace; consulte o procedimento de backup e restauração em `docs/HOSPEDAGEM.md`.

## Estado verificado em 07/11/2026

A autenticação da Vercel foi removida apenas do domínio de **produção** (a Loja mantém seu login); os previews continuam protegidos. No Supabase, o Flyway aplicou as migrações até V11. A Vercel usa Hikari com uma conexão por instância como mitigação de emergência. Foram observados cold starts de cerca de 18–21 segundos e o erro `EMAXCONNSESSION` por limite de clientes do pooler. O perfil `prod,railway` foi preparado, mas **não há deploy persistente homologado**.

## Limites: não é produção de loja comercial

Vercel Hobby é apenas pessoal/não comercial. Container Functions são efêmeras; o servidor pode parar após inatividade. Os trabalhos agendados e e-mail da Loja não funcionam nessa implantação sem um worker persistente e SMTP real. Para operação comercial, é necessário plano/host compatível, monitoramento, testes operacionais e backups externos. Use o Compose com PostgreSQL e HTTPS no servidor persistente conforme `docs/HOSPEDAGEM.md`.

## Mitigação de instâncias efêmeras (09/11/2026)

Os logs de produção mostraram muitos Java cold starts ao buscar `main-*.js`,
`styles-*.css` e demais arquivos estáticos; cada JVM criava conexões ao Supabase.
A origem Java ainda é uma Vercel Container Function, mas as requisições a
recursos públicos estáticos agora não criam sessão/CSRF e os arquivos com hash
recebem TTL de CDN. A configuração `prod,vercel` carrega beans sob demanda,
abre no máximo **1 conexão Hikari por instância** e fecha rapidamente a conexão
ociosa. HTML, APIs, fotos e qualquer dado autenticado **não são cacheados na CDN**.

**Atenção à evolução do banco:** a Vercel NÃO executa mais Flyway em cada
cold start. As migrações versionadas devem ser aplicadas de forma controlada
ao Supabase, com backup e verificação em um runner de manutenção confiável,
antes de publicar qualquer release que acrescente migrações. O backend
continua com `ddl-auto: validate`, que ajuda a detectar divergências.
Confirme a versão de `loja.flyway_schema_history` antes de novas releases;
na configuração atual V12 já está aplicada. Não use o runtime Vercel para
fazer migração concorrente em inúmeras instâncias.

**Limite importante:** essas medidas reduzem chamadas e conexões; não tornam
o processo Java persistente. Primeiro acesso após inatividade ainda terá
cold start; o plano Hobby permanece restrito a uso pessoal/não comercial.
O projeto pode servir para homologação sem custo, mas para operação comercial
contínua a plataforma não fornece as mesmas garantias de um servidor Java
persistente.

## Otimizações de navegação (10/11/2026)

- A sessão é reutilizada **apenas na memória do navegador por no máximo 30 segundos** entre rotas já autorizadas, evitando mais uma consulta em cada clique. Não há armazenamento de token/senha em localStorage, e os filtros de segurança no Java continuam verificando autenticação e revisões de conta em toda requisição ao backend.
- A lista de vendas agora consulta apenas o histórico inicial. O catálogo é carregado quando o caixa inicia um atendimento, e os clientes apenas quando o seletor opcional é aberto ou quando o operador pesquisa. Não há mudança em regras de pagamento/estoque e nenhuma escrita é repetida automaticamente.
- Retentativas de leitura continuam restritas a GET em falhas transitórias; POST/PUT/PATCH/DELETE nunca são repetidos automaticamente.

## Separação de interface estática e API (10/11/2026)

Para evitar que a Vercel tenha de inicializar a JVM em cada arquivo do Angular
(chunks JS de rotas, CSS, SVG), o projeto utiliza **Vercel Services**, recurso beta
disponível no Hobby. Mantém-se um único projeto Vercel, mesmo domínio e mesma
branch `main`. O Serviço `frontend` compila o Angular 22 para
`frontend/dist/frontend/browser` e publica arquivos estáticos na CDN.
O Serviço `backend` utiliza o `Dockerfile.api`, que compila apenas Spring Boot,
reduzindo o tempo de build. O endpoint `/api/**` e `/actuator/**`
continuam no Java, com as mesmas credenciais e cookies de sessão pelo mesmo domínio.

Rotas de navegação `/admin/**`, `/login` e demais páginas ficam no frontend;
o fallback da SPA aponta para `/index.html`. As regras de cache antigas para
JS/CSS permanecem válidas, sem cache dos dados pessoais ou da API.

**Limites:** abrir a interface estática não inicia mais o Java, mas login e API
ainda podem sofrer *cold start* por serem funções efêmeras. Vercel Services
é beta e seu uso conta para cotas padrão do Hobby; não torna Java persistente
nem altera a regra do plano gratuito para uso não comercial.

**Fallback:** se um deploy do beta falhar, restaure o `vercel.json` anterior
(sem `services`), mantenha o `Dockerfile` original, e reconfigure o framework
do projeto Vercel de `services` para `container`. Não há migração de dados.
