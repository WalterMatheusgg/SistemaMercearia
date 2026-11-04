# Ferragens

Base executável para gestão interna e loja virtual de uma loja de ferragens. O escopo desta etapa é autenticação por sessão e cadastro administrativo de produtos; os demais módulos estão planejados, não simulados.

## Estado desta entrega

Implementado: backend Java/Spring Boot, PostgreSQL com Flyway e `ddl-auto=validate`, login persistido, papéis, CSRF para SPA, sessão por cookie HttpOnly, erros RFC Problem Details, documentação OpenAPI, health check, CRUD administrativo paginado de produtos, categorias/unidades iniciais, interface Angular em pt-BR, proxy de desenvolvimento e Mailpit local.

Planejado: estoque e reservas, compras/recebimentos, clientes, vendas/orçamentos/caixa, pagamentos, contas, devoluções, catálogo público, carrinho/checkout, entrega/retirada, relatórios, auditoria e notificações comerciais. Não há saldo de estoque no produto, botões falsos, dados de demonstração de vendas nem envio externo de e-mail.

## Matriz de versões

As versões abaixo foram conferidas em documentação oficial e registros oficiais em 03/11/2026. Angular Core acompanha a versão de patch publicada separadamente de CLI/Material/CDK.

| Componente | Versão fixada ou gerenciada | Referência |
| --- | --- | --- |
| Java | 21 (nível de compilação; execução local validada em JDK 25) | [Spring Boot system requirements](https://docs.spring.io/spring-boot/4.1/system-requirements.html) |
| Spring Boot | 4.1.2 estável; dependências Spring gerenciadas pela BOM | [Spring Boot](https://spring.io/projects/spring-boot) |
| Maven Wrapper / Maven | 3.3.4 / 3.9.12 | [Maven Wrapper](https://maven.apache.org/wrapper/) |
| PostgreSQL | 18.6 (`postgres:18.6-alpine`) | [Versioning Policy](https://www.postgresql.org/support/versioning/) |
| Testcontainers | 2.0.5, resolvido pela BOM do Spring Boot 4.1.2 | [Spring Boot testcontainers](https://docs.spring.io/spring-boot/4.1/reference/testing/testcontainers.html) |
| springdoc-openapi | 3.2.0, linha compatível com Spring Boot 4 | [springdoc-openapi](https://springdoc.org/) |
| Angular Core / TypeScript | 22.2.4 / 6.0.3 | [Angular version compatibility](https://angular.dev/reference/versions) |
| Angular CLI / Material / CDK | 22.2.5 | [Angular releases](https://github.com/angular/angular/releases) |
| Node.js / npm | 24.22.2 / 11.19.1; Node 24 está na matriz suportada pelo Angular 22 | [Angular version compatibility](https://angular.dev/reference/versions) |
| Mailpit | `axllent/mailpit:v1.27.11` | [Mailpit](https://github.com/axllent/mailpit) |

As dependências Java usam o gerenciamento do Spring Boot; Testcontainers não declara versão própria no projeto. Angular, Material e CDK ficam exatos no `package.json` e `package-lock.json`.

## Requisitos locais

- Docker Engine e Docker Compose v2.
- JDK 21 ou superior para executar o wrapper (o compilador gera bytecode Java 21).
- Node.js 24.x e npm 11.x para o Angular 22.
- Acesso à internet na primeira execução para baixar imagens e dependências.

## Iniciar banco e Mailpit

```bash
cp /workspaces/SistemaMercearia/.env.example /workspaces/SistemaMercearia/.env
```

Edite `.env` e substitua `POSTGRES_PASSWORD`, `DB_PASSWORD` (devem coincidir) e `BOOTSTRAP_ADMIN_PASSWORD` por valores locais fortes. O arquivo fica ignorado pelo Git.

```bash
docker compose --env-file /workspaces/SistemaMercearia/.env -f /workspaces/SistemaMercearia/compose.yaml up -d postgres mailpit
docker compose --env-file /workspaces/SistemaMercearia/.env -f /workspaces/SistemaMercearia/compose.yaml ps
```

Portas locais: PostgreSQL `5432`, SMTP do Mailpit `1025`, interface/API web do Mailpit `8025`. Os serviços estão vinculados a `127.0.0.1`; o volume `postgres-data` preserva o banco. `docker compose down` mantém o volume; não use `-v` se quiser conservar os dados.

## Iniciar backend

Na raiz, exporte as variáveis do arquivo local e execute o wrapper:

```bash
set -a
. /workspaces/SistemaMercearia/.env
set +a
/workspaces/SistemaMercearia/mvnw -f /workspaces/SistemaMercearia/pom.xml -pl backend spring-boot:run
```

O perfil padrão é `local`. Na primeira execução, Flyway cria o schema e os dados iniciais. Com `BOOTSTRAP_ADMIN_ENABLED=true`, cria um usuário `ROLE_OWNER` apenas quando ainda não existe usuário; username e senha vêm do ambiente. Não há senha de produção embutida. Em ambiente seguro, defina `SESSION_COOKIE_SECURE=true` e termine HTTPS no proxy de mesma origem.

- API: `http://localhost:8080/api`
- OpenAPI UI: `http://localhost:8080/swagger-ui.html`
- Health mínimo: `http://localhost:8080/actuator/health`

## Iniciar frontend

Em outro terminal:

```bash
npm --prefix /workspaces/SistemaMercearia/frontend ci
npm --prefix /workspaces/SistemaMercearia/frontend start -- --host 0.0.0.0
```

Abra `http://localhost:4200`. No Codespaces, abra a aba **Ports**, encaminhe a porta `4200` e selecione **Open in Browser**; o proxy do Angular encaminha `/api` para `localhost:8080` no container. Encaminhe `8025` para abrir o Mailpit. Em produção, publique frontend e API na mesma origem. A autenticação usa cookie de sessão e CSRF; credenciais e tokens não são persistidos no `localStorage`.

## Usuários, papéis e endpoints

O bootstrap local cria o administrador configurado no `.env`, vinculado ao papel dono. Papéis planejados já cadastrados: `OWNER`, `MANAGER`, `SELLER`, `CASHIER`, `STOCKKEEPER`, `FINANCE` e `CUSTOMER`. O cadastro inicial de produtos exige `OWNER`, `MANAGER` ou `STOCKKEEPER`; o backend aplica a autorização.

- `GET /api/auth/csrf`, `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`.
- `GET /api/admin/categories`, `GET /api/admin/units`.
- `GET/POST /api/admin/products`, `GET/PUT/DELETE /api/admin/products/{id}`. `DELETE` inativa sem apagar o registro.
- Custo e preço existem somente no contrato administrativo. Valores monetários são enviados/retornados como strings decimais com ponto e duas casas (`"199.90"`), sem arredondamento implícito; valores com mais de duas casas são rejeitados.

## Migrações e testes

Migrações versionadas em `backend/src/main/resources/db/migration`. O Hibernate apenas valida (`ddl-auto=validate`); nunca gera schema.

```bash
/workspaces/SistemaMercearia/mvnw -f /workspaces/SistemaMercearia/pom.xml test
npm --prefix /workspaces/SistemaMercearia/frontend ci
npm --prefix /workspaces/SistemaMercearia/frontend test -- --watch=false
npm --prefix /workspaces/SistemaMercearia/frontend run build
```

Os testes Testcontainers precisam do daemon Docker e verificam PostgreSQL real, autenticação/autorização, CSRF, validação, persistência, logout e entrega SMTP ao Mailpit. A suíte também inclui teste unitário do serviço de produtos com Mockito.

Para enviar uma mensagem de smoke pela própria aplicação (somente perfil `local`), inicie o backend assim:

```bash
set -a
. /workspaces/SistemaMercearia/.env
set +a
APP_MAIL_SMOKE_TEST=true APP_MAIL_SMOKE_TEST_RECIPIENT=operacao@ferragens.local /workspaces/SistemaMercearia/mvnw -f /workspaces/SistemaMercearia/pom.xml -pl backend spring-boot:run
```

Confirme a mensagem em `http://localhost:8025`. Esse envio pela aplicação é separado do teste de integração, que usa instâncias próprias de PostgreSQL/Mailpit via Testcontainers.

Para recriar somente o container PostgreSQL e manter o mesmo volume, não execute `down -v`:

```bash
docker compose --env-file /workspaces/SistemaMercearia/.env -f /workspaces/SistemaMercearia/compose.yaml up -d --force-recreate postgres
```

O volume `postgres-data` não é removido por esse comando. Confirme a persistência consultando `product` depois que o container ficar saudável.

## Próximas etapas

Consulte [docs/ROADMAP.md](docs/ROADMAP.md), [docs/ARQUITETURA.md](docs/ARQUITETURA.md), [docs/MODELO_DADOS.md](docs/MODELO_DADOS.md) e [docs/REGRAS_NEGOCIO.md](docs/REGRAS_NEGOCIO.md). O desenho futuro de e-mail com outbox está em [docs/ARQUITETURA.md](docs/ARQUITETURA.md#notificacoes-por-email).