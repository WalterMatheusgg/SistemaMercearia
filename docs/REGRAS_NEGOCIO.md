# Regras de negócio

## Estoque e pedidos

- Saldo físico inicial de 50; entregar/vender 15 unidades resulta em 35.
- Receber mais 20 unidades resulta em 55. Uma compra ainda não recebida não altera o físico.
- Disponível = físico menos reservas ativas e quantidades indisponíveis (avaria, bloqueio ou outra classificação definida).
- Pedido online reserva disponibilidade. Na entrega, consome a reserva e baixa o físico exatamente uma vez.
- Operações simultâneas devem ser atômicas e não comprometer mais unidades do que a disponibilidade.
- Cancelamentos e devoluções preservam os eventos e documentos anteriores; devolução recebida gera movimento de entrada próprio, após inspeção.
- Produto não armazena um saldo editável. O saldo por local e o evento imutável são atualizados atomicamente; reservado/indisponível ficam zerados até seus fluxos serem implementados.
- Já implementado: saldo inicial, entrada/saída manual, perda, ajuste por contagem, mínimo e estorno integral. Pedido online, venda, pagamento e devolução comercial ainda estão planejados; movimento manual não representa esses documentos.
- Contagem requer a revisão consultada e não pode sobrescrever um movimento posterior. Estorno preserva o original e não pode ocorrer duas vezes nem gerar disponível negativo.
- Operação exige motivo e chave idempotente; repetição do mesmo comando retorna o evento original. O mínimo altera a configuração com variação física zero.

## Venda, financeiro e cliente

- Venda a prazo reconhece uma venda/faturamento; receber depois liquida o título existente e não duplica faturamento.
- Pix/cartão/transferência e dinheiro de gaveta são meios separados. Apenas dinheiro em espécie afeta o numerário da gaveta.
- Cada compra identificada pertence ao cliente correto, sem inferência por nome/telefone aproximado.
- Alteração posterior do cadastro ou preço não reescreve valores, descrições ou condições já snapshotados na venda.
- Itens, quantidades, preços, descontos e totais são calculados e validados no backend; o navegador é entrada de dados, não autoridade financeira.

## Precisão decimal e API

- Dinheiro: `BigDecimal` e `NUMERIC(14,2)`. Estoque: `BigDecimal` e `NUMERIC(19,4)`, validado pelo incremento da unidade (UN 1; M 0.01; KG/L 0.001). Unidade com histórico não pode ser trocada.
- Para dinheiro, valores negativos e mais de duas casas decimais são rejeitados. Não há arredondamento implícito; regras de arredondamento fiscal/comercial devem ser escolhidas antes de implementar descontos, impostos ou parcelamento.
- A API recebe e retorna dinheiro como string decimal com ponto e duas casas, por exemplo `"199.90"`, para não passar por ponto flutuante binário no JavaScript.
- Totais comerciais serão calculados no backend usando uma política de arredondamento explícita por operação; cliente não envia total confiável.

## Preservação e auditoria futura

- Inativar catálogo não remove produto referenciado por histórico.
- Cancelamento, estorno e devolução são novos fatos associados ao original, com autor, motivo e instante.
- Eventos operacionais/financeiros devem ser idempotentes e correlacionados ao documento de origem.

## Notificações

- O aviso de venda será publicado apenas após confirmação durável da venda via outbox.
- Falha de e-mail não invalida a venda. Tentativas, deduplicação, destinatários verificados, filtros e histórico estão descritos em [ARQUITETURA.md](ARQUITETURA.md#notificacoes-por-email).
