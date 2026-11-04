# Modelo de dados

| Migração | Entidades / garantias |
| --- | --- |
| V1 | Loja, usuário, papéis, categorias, unidades e produtos; dinheiro NUMERIC(14,2), identificadores UUID |
| V2 | Precisão/incremento por unidade, local, saldo por produto/local e razão imutável de movimentos |
| V3 | Configuração, contatos cliente/fornecedor, comandos idempotentes, auditoria, caixa/entradas, venda/itens, reservas, compras/itens/recebimentos, contas/pagamentos, tokens, preferências e outbox |
| V4 | Devolução integral de recebimento ao fornecedor, referência aos movimentos compensatórios e preservação do documento original |
| V5 | Dinheiro entregue e troco calculado na venda; somente o pagamento líquido movimenta a gaveta |

## Relações comerciais

Uma `party` representa CUSTOMER ou SUPPLIER. Cliente online tem `user_id` único; compras de balcão identificadas usam explicitamente esse cadastro. O sistema não associa compras por semelhança de nome/telefone.

`sale` guarda canal, status, cliente, subtotal/desconto/entrega/total, datas e snapshots em `sale_item`. Itens podem apontar para seu movimento físico. Pedido online tem `inventory_reservation` por item e prazo em `sale.expires_at`. Orçamento não cria reserva ou financeiro.

`purchase_order` e `purchase_item` guardam o pedido ao fornecedor. Cada `purchase_receipt` registra material recebido e tem linhas com movimentos de entrada. `purchase_return` compensa integralmente um recebimento, sem apagar o original.

`financial_account` guarda obrigação, vencimento, origem e valor pago. `financial_payment` registra pagamentos/recebimentos e compensações ligadas ao original. Cada venda tem um título; cada recebimento de compra tem outro. Dinheiro aponta para `cash_entry`; Pix/cartão/transferência não movimentam a gaveta.

`cash_session` tem abertura e conferência de fechamento. Um índice parcial permite uma única sessão aberta por loja. Entradas da gaveta são imutáveis; a posição é abertura + somatório de entradas/saídas.

## Estoque e precisão

Quantidades usam NUMERIC(19,4), validadas por unidade: UN inteiro; M passo 0.01; KG/L passo 0.001. Novas unidades têm precisão/incremento explícitos. Físico, reservado e indisponível nunca são negativos; reservado + indisponível não pode superar o físico. A chave de saldo é produto/local.

Movimentos têm snapshots de código/nome/unidade, quantidade, delta, valores anteriores/posteriores, motivo, ator, instante e chave idempotente. Índices únicos restringem abertura inicial e estorno. Trigger rejeita UPDATE/DELETE. A reserva é consumida/liberada por transição do pedido e seus eventos de auditoria.

Dinheiro é NUMERIC(14,2)/BigDecimal e trafega como string decimal. Totais de linhas usam HALF_UP para duas casas; recebimentos fracionados de compras usam diferença de totais acumulados para manter os centavos do pedido.

## Acesso, histórico e e-mail

`auth_revision` invalida sessões anteriores após mudança de acesso/senha. E-mail é único sem distinguir maiúsculas/minúsculas; o endereço precisa ser verificado para os fluxos que o exigem.

`account_token` contém somente hash do segredo e propósito/validade/uso. `notification_outbox` guarda destinatário, evento, mensagem, tentativas, lease e resultado; chave única usuário/evento. `notification_preference` configura os alertas por usuário. `audit_event` registra ator, entidade, motivo e instante sem senhas ou tokens.

O catálogo/local/caixa desta versão atendem uma loja principal; não há promessa de isolamento de catálogo multiloja. Não existem tabelas simuladas de tributos, gateway bancário ou emissão fiscal.
