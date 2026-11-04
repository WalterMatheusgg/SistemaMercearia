# Arquitetura

## Princípios

- Monólito modular Spring Boot; não há microsserviços nem mensageria distribuída nesta etapa.
- Pacote raiz `br.com.ferragens`; cada domínio contém API, serviço, persistência e modelo próprios. `shared` contém apenas erros HTTP comuns.
- Angular standalone com áreas reservadas para público, cliente e administração. Nesta entrega só a área administrativa de produtos está navegável.
- PostgreSQL é a fonte de verdade. Flyway versiona o schema e `ddl-auto=validate` detecta divergência sem criar/alterar tabelas.
- A autorização é aplicada no backend. Guards Angular melhoram navegação e não substituem regras da API.
- Sessão HTTP no servidor, cookie HttpOnly, `SameSite=Lax`, CSRF habilitado e renovado no login. Secure é configurável por ambiente. Frontend/API devem compartilhar a origem em produção; desenvolvimento usa proxy local.
- Valores monetários são `BigDecimal` e `NUMERIC(14,2)`. A API administrativa serializa decimais como strings JSON com duas casas; não arredonda valores de entrada acima de duas casas.

## Módulos

| Domínio | Situação | Responsabilidade prevista |
| --- | --- | --- |
| `auth` | Inicial | Usuário persistido, senha BCrypt, papéis, sessão/login/logout e consulta do usuário atual |
| `product` | Inicial | Produto, categoria, unidade e consultas administrativas paginadas |
| `customer` | Planejado | Cadastro, identidade, privacidade e histórico de compras |
| `inventory` | Planejado | Razão de movimentações, reservas, disponibilidade, inventário e alertas |
| `purchasing` | Planejado | Fornecedores, pedidos de compra e recebimento físico |
| `sales` / `checkout` | Planejado | Orçamentos, pedidos, venda presencial/online, caixa e transições auditáveis |
| `finance` | Planejado | Títulos, pagamentos, recebíveis e conciliação |
| `catalog` / `fulfillment` | Planejado | Catálogo público, carrinho, preço publicado, entrega e retirada |
| `notification` | Planejado | Preferências de alerta, outbox, tentativas e histórico |
| `reporting` / `audit` | Planejado | Relatórios, trilha de auditoria e consultas operacionais |

Evitar criar tabelas futuras sem contratos e invariantes definidos. Os diretórios públicos/cliente poderão compartilhar os DTOs de catálogo explicitamente publicados; nunca devem reutilizar DTO administrativo de produto.

## Dados e API

As tabelas iniciais e suas relações estão em [MODELO_DADOS.md](MODELO_DADOS.md). Categorias/unidades iniciais são referências editáveis posteriormente por endpoints próprios ainda não implementados. Produto não contém quantidade nem saldo de estoque.

Erros de validação, parsing, domínio e segurança são `ProblemDetail`. A API administrativa de produto inclui custo; não existe endpoint público de produto nesta etapa. Em endpoints públicos futuros, criar DTO de catálogo que omita custo e dados operacionais.

## E-mail

O `spring-boot-starter-mail` usa o SMTP local em `localhost:1025`, capturado pelo Mailpit na porta web `8025`. Não há destinatário externo nem fluxo de notificação comercial ativo. O teste de fumaça usa um endereço `.local`.

### Notificações por e-mail

Quando o domínio de vendas existir:

1. Armazenar destinatários verificados e preferências por tipo de evento/filtro, com consentimento e capacidade de desativação.
2. Na mesma transação que confirma a venda, inserir um evento imutável na tabela outbox com chave de deduplicação, payload mínimo e instante do evento.
3. Um processador em segundo plano reivindica eventos com lease/lock, envia em lote configurável ou por venda, registra tentativas, próximo retry e resultado.
4. Usar backoff limitado, quantidade máxima de tentativas e fila de falhas para inspeção/reenvio controlado. Deduplicação protege contra reprocessamento; e-mail é entrega pelo menos uma vez, não exatamente uma vez.
5. Falha de SMTP nunca reverte venda confirmada. O histórico registra evento, destinatário, template/versão, status e erro sanitizado; segredos e conteúdo sensível não entram nos logs.
6. Configurar remetente, destinatários, filtros, resumo versus aviso individual e limites por loja; validar destinatários antes de habilitar o canal.

Esta outbox e as configurações não foram criadas nesta fundação; serão definidas com a confirmação de vendas.

## Operação e segurança

- Desenvolvimento local em `http://localhost:4200` com proxy para `http://localhost:8080`; não habilitar CORS amplo.
- Produção planejada na mesma origem HTTPS, com `SESSION_COOKIE_SECURE=true`, segredo de bootstrap ausente/desabilitado e credenciais em secret manager.
- Não persistir senhas ou tokens no browser. BCrypt é usado para hash; papéis armazenados no PostgreSQL.
- Health endpoint não expõe detalhes internos. Endpoints de administração e Swagger exigem autenticação conforme a configuração.
- O Compose vincula portas a loopback e mantém dados num volume Docker local.