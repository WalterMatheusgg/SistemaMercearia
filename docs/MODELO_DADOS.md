# Modelo de dados

## Persistido nesta etapa

```mermaid
erDiagram
  STORE ||--o{ APP_USER : possui
  APP_USER ||--o{ USER_ROLE : recebe
  ROLE ||--o{ USER_ROLE : atribui
  CATEGORY ||--o{ PRODUCT : classifica
  UNIT ||--o{ PRODUCT : mede

  STORE {
    uuid id PK
    varchar name
    timestamptz created_at
  }
  APP_USER {
    uuid id PK
    uuid store_id FK
    varchar username UK
    varchar password_hash
    boolean enabled
    timestamptz created_at
  }
  ROLE {
    varchar code PK
    varchar name
  }
  USER_ROLE {
    uuid user_id PK,FK
    varchar role_code PK,FK
  }
  CATEGORY {
    uuid id PK
    varchar name UK
    boolean active
  }
  UNIT {
    varchar code PK
    varchar name
    boolean active
  }
  PRODUCT {
    uuid id PK
    varchar code UK
    varchar name
    uuid category_id FK
    varchar base_unit_code FK
    numeric cost
    numeric price
    boolean active
    timestamptz created_at
    timestamptz updated_at
  }
```

`product.cost` e `product.price` são `NUMERIC(14,2)` e não nulos/não negativos. IDs são UUID. `code` é único no banco e normalizado para maiúsculas na aplicação. `DELETE /api/admin/products/{id}` é inativação lógica. Não há coluna de quantidade ou estoque no produto.

V1 também insere uma loja principal, sete papéis, categorias iniciais e unidades `UN`, `M`, `KG` e `L`. Esses dados iniciais não são produtos nem estoque simulado.

## Estoque persistido pela V2

| Tabela / alteração | Dados e garantias |
| --- | --- |
| `unit.quantity_scale`, `quantity_step` | Precisão e incremento: UN 0/1, M 2/0.01, KG e L 3/0.001 |
| `inventory_location` | Local ativo vinculado à loja; cria o local principal sem quantidades |
| `inventory_balance` | Chave produto/local; físico, reservado, indisponível, mínimo, revisão e atualização. `NUMERIC(19,4)`; disponível não negativo |
| `inventory_movement` | Evento imutável com snapshots de produto/unidade, quantidade, variação, saldo/mínimo anterior e posterior, ator, motivo, instante, chave idempotente e referência de estorno |

O banco restringe saldo inicial único por produto/local, estorno único por movimento e chave idempotente única por loja. Um trigger rejeita UPDATE/DELETE do histórico. Não há saldos reais inseridos pela migração. Reservas e bloqueios têm colunas de saldo, mas ainda não têm tabelas de documentos ou operações próprias.

## Planejado, ainda sem tabelas

| Área | Entidades a modelar quando o módulo começar |
| --- | --- |
| Produtos | Marca, variação/SKU, embalagem e fator de conversão, quando casos de uso confirmarem cardinalidade |
| Clientes | Cliente, endereços, contatos verificados e identificadores protegidos |
| Compras | Fornecedor, pedido de compra, item, recebimento parcial e documento de origem |
| Estoque | Documento de reserva, bloqueio, lote quando necessário, transferência e sessão de inventário |
| Venda | Orçamento, pedido, venda, itens com snapshot de nome/código/preço/tributação e cancelamento/devolução |
| Caixa e pagamentos | Sessão de caixa, lançamento por meio, liquidação, título a pagar/receber e conciliação |
| Entrega | Opção de fulfillment, reserva, coleta/expedição e comprovante de entrega |
| Auditoria | Evento de domínio/ator/instante/correlação com política de retenção definida |
| E-mail | Preferência/contato verificado, outbox, tentativas e histórico de envio |

## Invariantes para o desenho futuro

- Registrar entradas/saídas como movimentos; correções geram movimentos compensatórios, não edição destrutiva do passado.
- Reservar com transação concorrente e bloqueio/controle otimista de disponibilidade; nunca aceitar comprometimento maior que disponível.
- Snapshot dos dados comerciais por linha de venda para preservar o histórico após alteração do cadastro/preço.
- Separar fato de venda, recebimento e liquidação financeira; pagamento posterior não cria outra venda.
- Outbox comercial deve ser gravada junto da transação do domínio que publica o evento.
