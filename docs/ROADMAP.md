# Roadmap

## Implementado nesta preparação

- Monorepo, Maven Wrapper, lockfile npm, Compose local para PostgreSQL/Mailpit e perfis Spring.
- Migração inicial com loja, usuário, papel, categoria, unidade e produto.
- Login persistido com BCrypt, sessão servidor, CSRF, consulta atual, logout e autorização por papel.
- CRUD administrativo paginado de produtos, erros ProblemDetail, OpenAPI e health mínimo.
- Angular Material standalone: login, navegação administrativa, pesquisa/paginação, cadastro, edição e inativação conectados ao backend.
- Testes JUnit/Mockito e integração Testcontainers com PostgreSQL/Mailpit.

## Próximas entregas

1. Política de usuários/permissões, auditoria administrativa, recuperação de senha e configuração de loja.
2. Fornecedores e compras, recebimento parcial e registro imutável de movimentos de estoque.
3. Reservas concorrentes, disponibilidade, contagens, inventário e alertas operacionais.
4. Clientes, orçamento, venda de balcão, snapshots comerciais e caixa por meio de pagamento.
5. Contas a pagar/receber, liquidação, estorno e relatórios financeiros reconciliáveis.
6. Catálogo público sem custo, cliente, carrinho, pedido, retirada e entrega.
7. Preferências de notificação e outbox transacional com processamento, deduplicação, retries limitados e histórico.
8. Endurecimento de produção: HTTPS, cookie Secure, segredos gerenciados, backup/restore testado, observabilidade, auditoria, rate limiting, CI e testes de navegador.

## Critérios antes de avançar um domínio

- Regras e estados documentados, incluindo cancelamento, concorrência, idempotência e histórico.
- Migração reversível/compatível com dados existentes e política de retenção/privacidade definida.
- Casos concorrentes e de autorização exercitados em PostgreSQL real.
- API explícita para público/admin; DTOs não compartilham campos internos por conveniência.
- UI não representa como concluída uma integração inexistente.