# Situação da entrega

Os fluxos implementados, regras, papéis e limitações estão em [OPERACAO.md](OPERACAO.md). Para executar/atualizar no Codespace, consulte [CODESPACE.md](CODESPACE.md).

## Implementado

- Fundação Java/Spring/Angular/PostgreSQL, migrações V1/V2/V3, sessão/CSRF, papéis, erro estruturado e CI.
- Cadastro de produtos, unidades/categorias, estoque auditado e controle concorrente.
- Clientes/fornecedores, usuários, desativação com revogação de sessão, recuperação e verificação de e-mail.
- Vendas de balcão/orçamento, caixa, pagamento manual dividido/a prazo e devolução integral.
- Compras e recebimentos parciais vinculados ao estoque e a contas a pagar.
- Contas, liquidação parcial, estorno, relatórios e CSV.
- Catálogo, carrinho, conta do cliente, pedido com reserva/expiração e entrega/retirada.
- Alertas configuráveis, outbox, retries e histórico, resumo diário.
- Docker integrado, perfil de produção, scripts de backup/restauração e testes de navegador no CI.

## Dependências para produção

Hospedagem/domínio/HTTPS, SMTP real, dados reais do cliente e backup externo. Cobrança automática e emissão fiscal dependem de provedores/credenciais e homologação; não fazem parte do registro manual entregue.

## Ampliações

Filiais/múltiplos caixas, transferência entre locais, operação offline, bloqueios/lotes, devoluções parciais, frete por transportadora, conciliação bancária automática, gateway de pagamento e integração fiscal. Priorizar conforme a operação do cliente.
