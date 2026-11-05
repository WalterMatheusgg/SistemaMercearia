# Operação e limites da entrega

## Fluxos implementados

| Área | Operação |
| --- | --- |
| Acesso | Login/sessão/CSRF, contas de funcionários por papel, desativação com invalidação de sessão, cadastro de cliente, verificação de e-mail e recuperação por link de uso único |
| Catálogo | Produtos, marca opcional, categorias, novas unidades com precisão, filtros e cadastros a revisar, inativação e catálogo público sem custo interno |
| Estoque | Saldo inicial, entrada/saída/perda, mínimo, contagem, histórico imutável, estorno manual, proteção contra saldo negativo, acesso à edição pelo estoque e reposição sugerida até 2× o mínimo, sem criação automática de compras |
| Pessoas | Clientes e fornecedores, contatos, inativação e histórico de documentos/contas vinculadas |
| Venda | Balcão, orçamento com validade de 7 dias, confirmação, desconto autorizado, pagamentos divididos, venda a prazo e devolução integral |
| Caixa | Um caixa físico por loja, abertura, suprimento, sangria, lançamentos em dinheiro e fechamento com diferença justificada e troco calculado no servidor |
| Compras | Pedido ao fornecedor, recebimento parcial/completo, entrada física e contas a pagar apenas do material recebido e devolução integral de recebimentos |
| Financeiro | Títulos avulsos e de origem comercial, pagamentos parciais, estorno e saldos sem duplicar faturamento |
| Loja virtual | Catálogo, carrinho, cliente verificado, cálculo no servidor, reserva com expiração, pedido, conferência manual de pagamento, preparo e retirada/entrega |
| Relatórios | Vendas por data, ticket, produtos, pagamentos por meio, posição de contas, custo histórico das mercadorias vendidas, lucro e margem brutos estimados, estoque físico avaliado a custo, comparativo de períodos e materiais sem vendas há 90 dias (com cadastro mais antigo que 90 dias), exportação CSV |
| E-mail | Preferências por usuário, verificação do destinatário, outbox transacional, reenvios automáticos limitados e histórico; resumo às 21h de Brasília |
| Operação | Docker integrado, perfil de produção, saúde, CI, testes de navegador, backup e restauração em banco separado |

## Regras comerciais

- O servidor lê o preço do cadastro e calcula cada linha com `quantidade × preço`, arredondamento HALF_UP para duas casas. O desconto global exige dono/gerente e não pode superar o subtotal. A interface exige cálculo prévio e envia o total esperado, recusando alterações de preço entre cálculo e confirmação.
- Orçamento preserva preços e não movimenta estoque/financeiro. Sua confirmação consulta disponibilidade e usa o preço combinado no orçamento.
- Venda de balcão exige caixa aberto. Sem pagamento integral, exige cliente identificado e cria o saldo a receber. Informe o valor líquido destinado à venda e, opcionalmente, o dinheiro entregue pelo cliente; o servidor calcula o troco, e somente o valor líquido altera a gaveta.
- Apenas dinheiro movimenta a gaveta. Pix/cartão/transferência são registros manuais de operações feitas e conferidas fora do sistema. Não há cobrança nem reembolso bancário automático.
- Cancelamento/devolução de venda é integral: exige gerência e motivo, restaura estoque e gera compensações dos pagamentos. Faça a devolução física e o reembolso externo antes de confirmar seu registro. Estorno de dinheiro exige saldo suficiente no caixa atual.
- Pedido de compra não aumenta o físico nem cria dívida de material ainda não recebido. Cada recebimento cria sua própria conta. Em medidas fracionadas, o custo dos recebimentos é calculado pela diferença entre totais acumulados arredondados para evitar centavos extras no fracionamento.
- Pedido online exige e-mail verificado. Reserva disponibilidade, sem reduzir o físico; confirmar recebimento integral consome a reserva e baixa o físico. Cancelamento pendente e expiração liberam o reservado. O processador verifica vencimentos a cada minuto.
- Entrega usa taxa fixa configurada e endereço obrigatório. Retirada permite combinar pagamento presencial. A ordem de andamento é pago → pronto → entregue/retirado; entrega inclui a etapa em transporte.
- Itens comerciais guardam snapshots de código/nome/unidade/preço/custo. Mudanças de cadastro não reescrevem documentos. Unidade com estoque ou documento comercial não pode ser trocada.
- Escritas comerciais exigem `Idempotency-Key` UUID e transação atômica. Reenviar a mesma operação não duplica venda, pagamento ou recebimento. O navegador mantém a chave/corpo quando a resposta fica incerta e protege a saída da tela.

## Papéis

| Papel | Acesso principal |
| --- | --- |
| Dono | Todos os módulos; administração de donos/gerentes |
| Gerente | Gestão operacional; não altera donos/gerentes |
| Vendedor | Clientes, vendas, consulta de estoque/caixa |
| Caixa | Clientes, vendas e operação do caixa |
| Estoquista | Produtos, estoque, fornecedores e recebimentos; não cria pedido de compra nem vê financeiro |
| Financeiro | Clientes/fornecedores, contas e relatórios |
| Cliente | Próprio perfil e compras/pedidos vinculados |

Guards são conveniência de navegação; a API aplica as permissões e resolve o ator pela sessão. Nenhum cliente pode consultar pedidos de outro usuário. Os endpoints públicos não compartilham DTOs de custo ou dados administrativos.

## Produção e serviços externos

A aplicação é funcional localmente para esses fluxos, mas a entrada em produção exige configurar e validar os serviços do cliente:

- Provedor de hospedagem, domínio, HTTPS e política de acesso. Codespace é ambiente de desenvolvimento, não hospedagem definitiva.
- PostgreSQL com backup externo, retenção, recuperação testada e monitoramento.
- SMTP real, remetente autorizado, TLS/credenciais e testes de entrega. Mailpit é somente captura local; não é um serviço de envio externo.
- Cobrança automática por Pix/cartão e emissão fiscal **não estão integradas**. Exigem escolha do provedor, conta/credenciais, contrato técnico e homologação. O comprovante imprimível é não fiscal.
- O perfil `prod` usa cookie Secure e desliga OpenAPI; use HTTPS na mesma origem. O arquivo `infra/compose.production.yaml` não publica o banco/SMTP e vincula o app a loopback para um proxy HTTPS.
- Para o primeiro dono em ambiente vazio, use temporariamente os perfis `prod,bootstrap` e as variáveis de bootstrap; retire o perfil e o segredo após criar a conta. O bootstrap não redefine usuários existentes.
- Limite de tentativas de autenticação é por processo/IP. Em várias instâncias, use limitador compartilhado no proxy e planeje persistência/afinidade de sessão.

Esta versão atende uma loja, um local principal e um caixa físico. Ainda não oferece operação offline, filiais, múltiplos caixas, frete por transportadora, tributos, conciliação automática, devolução parcial de venda ou transferência entre locais. São ampliações de escopo, não botões simulados.
