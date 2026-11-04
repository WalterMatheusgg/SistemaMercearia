# Regras de negócio

As regras operacionais completas estão em [OPERACAO.md](OPERACAO.md). Este resumo registra as invariantes aplicadas pela implementação.

- Estoque: 50 − saída 15 = 35; entrada 20 = 55. Compra ainda não recebida não altera o físico. Reservas reduzem disponibilidade e expiram ou são consumidas/liberadas pelo pedido.
- Concorrência: bloquear produtos em ordem, preservar saldo não negativo e gravar documento/saldo/financeiro/auditoria/outbox na mesma transação. Contagens antigas não sobrescrevem saldos recentes.
- Idempotência: uma chave UUID confirma o mesmo comando uma única vez. Conteúdo/ator/operação incompatíveis com a chave são recusados. Uma resposta de rede incerta não autoriza uma segunda baixa.
- Histórico: inativação preserva cadastros; devolução/estorno preservam documentos originais e geram compensações. Itens comerciais têm snapshots; unidade já utilizada fica bloqueada.
- Dinheiro: BigDecimal/NUMERIC(14,2), strings na API e entrada com até duas casas. Linhas quantidade × preço usam HALF_UP explícito. Preço e total são recalculados no servidor; a interface confere o total esperado.
- Caixa: espécie é separada de Pix/cartão/transferência. Dinheiro entregue e troco ficam registrados, mas apenas o valor líquido da venda entra na gaveta. Sangria, pagamento e estorno não podem tornar a gaveta negativa.
- Financeiro: venda a prazo cria uma venda e um recebível; receber depois liquida o título, sem novo faturamento. Compras geram contas apenas pelo material recebido. Estornos não apagam o lançamento anterior.
- Cliente: compras identificadas usam o cliente escolhido, sem associação aproximada por nome. Usuário cliente só lê seus próprios dados/documentos. Pedido online exige e-mail verificado.
- E-mail: evento nasce na transação do domínio, envio ocorre fora dela. Falha SMTP não desfaz venda. Preferências, destinatário verificado, papel e acesso ativo são rechecados no envio; retries são limitados.
- Pagamentos externos: o operador confere recebimento/reembolso no banco/adquirente antes de registrar. O sistema não simula liquidação automática, emissão fiscal ou autorização de um provedor inexistente.
