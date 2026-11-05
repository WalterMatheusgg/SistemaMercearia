# Loja — Controle interno de ferragens

**Loja** é um sistema de controle para ferragens: produtos sem código manual, estoque e movimentações, compras, fornecedores, vendas internas, caixa, contas, relatórios e perfis por funcionário. O antigo carrinho, a vitrine pública e o cadastro externo não são publicados por padrão; os registros históricos e serviços internos permanecem preservados.

## Começar no Codespace

```bash
cd /workspaces/SistemaMercearia
git fetch origin
git switch main
git pull --ff-only origin main
bash scripts/start.sh
```

Abra a porta **8080** na aba **Ports**. O script compila e sobe o app completo no Docker, preservando o banco e o `.env` existentes. Em ambiente novo cria senhas locais aleatórias: consulte o login administrativo no `.env`. O acesso é interno: a porta 8080 abre o login para usuários autorizados. Contas administrativas entram no painel de controle. E-mails de desenvolvimento aparecem no Mailpit, porta **8025**.

**Precisa criar ou recuperar acesso de dono?** Use `python3 scripts/owner-setup.py` no terminal privado do Codespace. O comando salva backup, verifica o perfil Docker local, cria `admin` (OWNER) se faltar ou redefine a senha de um `admin` já dono, com nova senha forte gerada e mostrada somente no terminal. Não altera outros usuários, vendas, estoque ou volumes. Para apenas conferir a senha inicial, use `bash scripts/admin-access.sh`. O usuário padrão é `admin`; por segurança não existe senha universal `admin/admin`. Veja a experiência visual em [docs/DESIGN_LOJA.md](docs/DESIGN_LOJA.md).

Instruções completas: [docs/CODESPACE.md](docs/CODESPACE.md). Conferência segura de funcionamento: `bash scripts/smoke.sh`. Antes da hospedagem, execute `python3 scripts/preflight-production.py .env`. Entrega e pendências: [docs/ENTREGA_FINAL.md](docs/ENTREGA_FINAL.md). Fluxos, papéis e limites: [docs/OPERACAO.md](docs/OPERACAO.md).

**Hospedagem comercial e HTTPS:** antes de publicar, consulte [docs/HOSPEDAGEM.md](docs/HOSPEDAGEM.md). A Vercel Hobby gratuita não autoriza uso comercial; a implantação Docker com Caddy mantém API e interface na mesma origem. Nenhuma URL externa foi homologada.

**A aplicação agora é para controle interno, sem checkout público.** Pagamentos são registrados e conferidos manualmente pela loja. Cobrança/reembolso bancário automático, emissão fiscal e publicação em domínio real dependem de integrações e credenciais externas. Não há credenciais de produção embutidas nem emissão fiscal simulada. A versão atende uma loja/local principal e um caixa físico.

## Navegação renovada

A interface organiza as tarefas em **Estoque, Produtos, Fornecedores, Vendas e Financeiro**, com acesso à área de Compras dentro de Estoque, sem perder históricos. Dentro do **Financeiro**, a aba Dinheiro físico permite entradas e retiradas sem abertura/fechamento manual. A nova pesquisa universal abre com `Ctrl+K` (ou `⌘K` no macOS); o painel mostra indicadores reais e ações rápidas para a operação.

- **Produtos:** foto obrigatória no cadastro manual, via câmera ou arquivo JPG/PNG/WebP de até 3 MB. Nome, categoria, unidade, preço e **quantidade inicial** podem ser preenchidos na mesma tela, sem código manual. O nome pode ficar vazio, gerando um material provisório. Categorias e unidades podem ser criadas dentro de Produtos.
- **Vendas:** três partes — escolher itens, conferir total e confirmar. Abertura de caixa deixa de ser uma etapa do atendimento.
- **Estoque:** alternância entre cartões ilustrados e tabela, com preferência salva no navegador. Nos cartões, use Entrada, Saída, Histórico ou Mínimo. Movimentações exigem quantidade e motivo, e são auditadas.
- **Financeiro:** duas abas — Contas e pagamentos / Dinheiro físico — para reunir registros sem duplicar o menu. **Relatórios:** períodos rápidos, gráficos e exportação CSV.
- **Assistente:** consultas locais e recomendações sobre o estado real da loja; gestores visualizam falhas HTTP 5xx recentes sem dados sensíveis. Há **prévia de importação de até 200 materiais por lote**, com nomes duplicados ignorados e confirmação obrigatória. Os dez materiais de exemplo podem ser carregados manualmente e têm prefixo TESTE; **nada é inserido automaticamente**. Materiais importados em lote sem foto usam imagem provisória até receberem uma foto no editor.

### IA opcional dentro da Loja

O assistente sem chave externa responde perguntas comuns sobre dados atuais. Para habilitar **interpretação com modelo de IA**, adicione `OPENAI_API_KEY` no **`.env` privado**, sem enviar o valor ao GitHub, e, opcionalmente, `OPENAI_MODEL`. Depois execute `bash scripts/start.sh` para aplicar as variáveis ao container.

A IA só é acionada se o usuário **marcar explicitamente a opção** na tela; nesse caso, o servidor envia o texto da pergunta e um resumo dos dados visíveis ao seu perfil ao provedor do modelo. Não são enviados dados de outros perfis. O modelo não recebe permissão de escrita e nunca faz alterações diretamente. Para importar, use lista `Nome;preço;custo` (até 200 linhas), revise a prévia e confirme a gravação. As alterações comerciais continuam sob regras, permissões e auditoria do backend.

A inspiração visual é o site da SEA Tecnologia: hierarquia tipográfica forte, elementos de conexão e navegação interativa — **sem copiar a interface nem usar dados fictícios**.

## Matriz de versões

As versões abaixo foram conferidas em documentação oficial e registros oficiais em 03/11/2026. Angular Core acompanha a versão de patch publicada separadamente de CLI/Material/CDK.

| Componente | Versão fixada ou gerenciada | Referência |
| --- | --- | --- |
| Java | 21 (nível de compilação; testes/execução no CI em JDK 21) | [Spring Boot system requirements](https://docs.spring.io/spring-boot/4.1/system-requirements.html) |
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
- OpenAPI UI: `http://localhost:8080/swagger-ui.html` (exige sessão autenticada na mesma origem)
- Health mínimo: `http://localhost:8080/actuator/health`

## Iniciar frontend

Em outro terminal:

```bash
npm --prefix /workspaces/SistemaMercearia/frontend ci
npm --prefix /workspaces/SistemaMercearia/frontend start -- --host 0.0.0.0
```

Abra `http://localhost:4200`. No Codespaces, abra a aba **Ports**, encaminhe a porta `4200` e selecione **Open in Browser**; o proxy do Angular encaminha `/api` para `localhost:8080` no container. Encaminhe `8025` para abrir o Mailpit. Em produção, publique frontend e API na mesma origem. A autenticação usa cookie de sessão e CSRF; credenciais e tokens não são persistidos no `localStorage`.

## Usuários, papéis e endpoints

O bootstrap local cria o administrador configurado no `.env`, vinculado ao papel dono. Papéis cadastrados: `OWNER`, `MANAGER`, `SELLER`, `CASHIER`, `STOCKKEEPER`, `FINANCE` e `CUSTOMER`. O cadastro inicial de produtos exige `OWNER`, `MANAGER` ou `STOCKKEEPER`; o backend aplica a autorização.

- `GET /api/auth/csrf`, `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`.
- `GET /api/admin/categories`, `GET /api/admin/units`.
- `GET/POST /api/admin/products`, `GET/PUT/DELETE /api/admin/products/{id}`. `DELETE` inativa sem apagar o registro.
- Custo e preço existem somente no contrato administrativo. Valores monetários são enviados/retornados como strings decimais com ponto e duas casas (`"199.90"`), sem arredondamento implícito; valores com mais de duas casas são rejeitados.

## Usar o estoque

No cadastro de um produto novo, você já pode informar a quantidade inicial e enviar a fotografia; o sistema salva tudo na mesma transação. Para alterações posteriores, abra **Estoque**. O local principal é criado pela migração V2, sem saldo fictício. Registre um saldo inicial de 50 e uma saída manual de 15: o físico e o disponível passam a 35. Uma entrada posterior de 20 leva o saldo a 55. Toda operação exige um motivo e guarda responsável, instante e valores anteriores/posteriores.

- `UN` aceita inteiros; `M` aceita incrementos de `0.01`; `KG` e `L`, de `0.001`. Quantidades via API são strings decimais, sem cálculo em ponto flutuante no navegador.
- Dono/gerente/estoquista registram saldo inicial, entrada, saída, perda e mínimo. Contagem e estorno são restritos ao dono/gerente. Vendedor/caixa também têm consulta na interface e na API, sem botões de movimentação manual.
- Saldo inicial só pode preceder movimentos físicos. Contagem informa o total contado, inclusive zero, e exige a revisão consultada; se outro movimento ocorrer, confira novamente antes de confirmar.
- Saídas e estornos que deixariam o disponível negativo são recusados integralmente. Correções não apagam o original. O mínimo gera evento com variação física zero e filtro visual para reposição; alertas por e-mail seguem as preferências de destinatários verificados.
- Pedidos online alteram o reservado e o consomem/liberam na confirmação, cancelamento ou expiração. O fluxo de bloqueio/indisponível ainda não está exposto.
- A unidade de um produto com histórico de estoque não pode ser alterada. Cadastre outro produto para trabalhar com uma unidade diferente.

API: `GET /api/admin/inventory/locations`, `GET /api/admin/inventory/balances?locationId=...`, `GET /api/admin/inventory/movements?locationId=...` e `POST /api/admin/inventory/movements`. O POST exige CSRF e cabeçalho `Idempotency-Key` com UUID. Reenvie a mesma chave e corpo para confirmar uma operação incerta: ela não será aplicada novamente. Uma chave reutilizada com outro conteúdo retorna 409.

O contrato completo e exemplos estão em [docs/ESTOQUE.md](docs/ESTOQUE.md).

## Migrações e testes

Migrações versionadas em `backend/src/main/resources/db/migration`. O Hibernate apenas valida (`ddl-auto=validate`); nunca gera schema.

```bash
/workspaces/SistemaMercearia/mvnw -f /workspaces/SistemaMercearia/pom.xml test
npm --prefix /workspaces/SistemaMercearia/frontend ci
npm --prefix /workspaces/SistemaMercearia/frontend test -- --watch=false
npm --prefix /workspaces/SistemaMercearia/frontend run build
```

Os testes Testcontainers precisam do daemon Docker e verificam PostgreSQL real, autenticação/autorização, CSRF, validação, persistência, logout, proteção do OpenAPI, estoque concorrente, rollback, idempotência, precisão por unidade e entrega SMTP ao Mailpit. Cada classe usa um schema aleatório isolado. A suíte também inclui teste unitário do serviço de produtos com Mockito e testes de componentes/HTTP Angular. O workflow `CI` executa testes e builds em pushes e pull requests.

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

Consulte [docs/ROADMAP.md](docs/ROADMAP.md), [docs/ARQUITETURA.md](docs/ARQUITETURA.md), [docs/MODELO_DADOS.md](docs/MODELO_DADOS.md) e [docs/REGRAS_NEGOCIO.md](docs/REGRAS_NEGOCIO.md). O desenho de e-mail com outbox está em [docs/ARQUITETURA.md](docs/ARQUITETURA.md#notificacoes-por-email).
