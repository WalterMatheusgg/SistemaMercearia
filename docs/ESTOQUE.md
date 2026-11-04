# Estoque

## Operações disponíveis

| Tipo na API | Efeito | Permissão para registrar |
| --- | --- | --- |
| `INITIAL` | Define a abertura somando a quantidade ao saldo zero, antes de qualquer movimento físico | Dono, gerente ou estoquista |
| `ENTRY` | Soma a quantidade recebida manualmente | Dono, gerente ou estoquista |
| `EXIT` | Subtrai uma saída manual; não cria venda ou pagamento | Dono, gerente ou estoquista |
| `LOSS` | Subtrai perda com motivo | Dono, gerente ou estoquista |
| `COUNT` | Usa o total contado como novo físico; exige revisão atual | Dono ou gerente |
| `MINIMUM` | Altera o limite visual de reposição sem variar o físico | Dono, gerente ou estoquista |
| `REVERSAL` | Compensa integralmente um evento anterior, uma única vez | Dono ou gerente |

Consultas também são autorizadas na API para vendedor e caixa. A área administrativa Angular atual é acessível a dono/gerente/estoquista. Não existe gestão de usuários nesta etapa; o bootstrap cria o dono.

## Exemplo de comando

Depois de autenticar e obter CSRF, envie `POST /api/admin/inventory/movements` com `Content-Type: application/json`, `X-XSRF-TOKEN` e `Idempotency-Key` (UUID único da operação):

```json
{
  "productId": "UUID_DO_PRODUTO",
  "locationId": "00000000-0000-0000-0000-000000000201",
  "kind": "INITIAL",
  "quantity": "50",
  "reason": "Contagem da implantação",
  "expectedRevision": null,
  "reversesMovementId": null
}
```

Uma `EXIT` de `"15"` com outra chave leva o físico a `"35"`; uma `ENTRY` de `"20"` leva a `"55"`. O servidor calcula e confirma os valores. Consulte `GET /api/admin/inventory/balances?locationId=...` para o saldo e `GET /api/admin/inventory/movements?locationId=...&productId=...` para o histórico. Ambas as consultas aceitam `page`/`size` (máximo 100). Saldos aceitam `query`, `onlyLow` e `includeInactive`; histórico aceita `kind`.

Na contagem, `quantity` é o total contado, inclusive zero, e `expectedRevision` vem da consulta de saldo. Se a revisão estiver desatualizada, retorna 409: feche o formulário, atualize e confira a contagem. No estorno, informe `reversesMovementId` e a quantidade absoluta da variação original; não são permitidos estorno de outro estorno, de configuração de mínimo ou de evento sem variação.

## Integridade e repetição

- Disponível = físico − reservado − indisponível; nenhuma operação pode deixá-lo negativo. Reservado e indisponível ainda não têm fluxo de alteração.
- O saldo e o evento são gravados na mesma transação PostgreSQL. Bloqueios serializam alterações concorrentes no produto/saldo, inclusive na abertura inicial.
- A mesma chave, corpo e usuário retornam o mesmo evento. Reutilizar a chave com outro conteúdo ou usuário retorna 409. Após falha de conexão/5xx, o formulário preserva corpo e chave para confirmar a mesma operação.
- O histórico guarda nome/código/unidade originais, usuário, motivo e instante. O banco rejeita edição/exclusão; uma correção gera outro registro. Mudança posterior de nome não reescreve o histórico.
- A unidade fica bloqueada quando há qualquer histórico. Itens inativos podem ser consultados e ter saídas/correções, mas não novas entradas nem abertura.
- O mínimo registra evento de configuração com delta zero. `EMPTY` significa disponível zero; `LOW`, disponível positivo menor ou igual ao mínimo; `NORMAL`, acima do mínimo. O filtro de reposição inclui itens zerados.
- A API resolve loja e usuário pela sessão; não aceita ator/loja declarados pelo navegador. Local de outra loja é recusado. O catálogo atual é único para a loja; gestão multiloja não foi entregue.

## Testes

Execute `./mvnw verify` com Docker ativo e os testes Angular descritos no README. A integração usa PostgreSQL 18 e Mailpit reais, com schema aleatório por classe. Abrange contas, insuficiência, rollback, duas saídas simultâneas, abertura concorrente, contagem desatualizada, unidades, idempotência, histórico imutável, estorno, escopo de loja e papéis via HTTP.

Opcionalmente, em ambiente sem Docker, os testes aceitam serviços dedicados configurados por `FERRAGENS_TEST_JDBC_URL`, `FERRAGENS_TEST_DB_USERNAME`, `FERRAGENS_TEST_DB_PASSWORD`, `FERRAGENS_TEST_MAILPIT_HTTP_URL`, `FERRAGENS_TEST_SMTP_HOST` e `FERRAGENS_TEST_SMTP_PORT`. O usuário de banco precisa poder criar schemas. Não aponte para produção; os testes deixam seus schemas isolados para diagnóstico. O padrão continua sendo Testcontainers.

## Limites desta etapa

Ainda não há compra por fornecedor, reserva de pedido, transferência entre locais, venda/caixa, pagamento, devolução comercial, alertas por e-mail ou inventário em lote. A tela identifica a saída como manual para não confundir movimentação física com faturamento.
