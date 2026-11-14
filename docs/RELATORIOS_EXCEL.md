# Exportação gerencial Excel (.xlsx)

Em **Relatórios**, selecione **Hoje**, **Últimos 5 dias**, **Últimos 7 dias**,
**Últimos 30 dias**, **Este mês** ou **Escolher datas**. Para intervalo manual,
informe início/fim e clique em **Aplicar período**. O botão **Exportar planilha
Excel (.xlsx)** baixa um arquivo com o período aplicado.

## Abas exportadas

- **Resumo:** faturamento, quantidade de vendas, ticket médio, custo de mercadorias,
  lucro bruto estimado, movimentações e posição atual de contas e estoque.
- **Vendas / Itens vendidos:** operações confirmadas no período com valores,
  descontos e custos históricos dos itens; exclui canceladas e orçamentos.
- **Entradas e saídas:** movimentações reais e reversões por data da operação,
  contendo quantidades com sinal, saldos anteriores/posteriores, operador e motivo.
- **Estoque atual:** saldo físico, reservado, disponível, mínimos e valor a custo.
  É posição do momento do download, **não histórico retroativo**.
- **Pagamentos:** recebimentos e desembolsos por data e meio, incluindo reversões.
- **Contas:** contas criadas no período; status, valor pago e saldo são **atuais**.
- **Movimentos de caixa:** eventos físicos de caixa; podem referir-se a pagamentos
  já mostrados em **Pagamentos**. Não some as duas abas.
- **Compras / Receb. compras:** pedidos criados e recebimentos de materiais no período.

Todos os horários apresentados são convertidos para **America/Sao_Paulo**.
O período máximo é de **2 anos**. Por proteção contra arquivos e consultas excessivos,
cada aba detalhada admite **até 5.000 registros**; acima disso, a API informa
que é necessário dividir o período. Nenhuma informação é omitida silenciosamente.

A rota é somente leitura: `GET /api/admin/reports.xlsx?start=AAAA-MM-DD&end=AAAA-MM-DD`,
autorizada para **OWNER, MANAGER e FINANCE**, do mesmo modo que os relatórios.
Todas as consultas isolam dados por `store_id`; download com `Cache-Control: no-store`.
Não exige migração de banco, serviços pagos nem alteração de branch.
