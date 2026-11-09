# Etiquetas e leitura de código de barras — Loja Ferragens

## O que foi implementado

- **Cada produto recebe automaticamente um código curto único** no formato `LF0000000001` (12 caracteres). A sequência é gerada pelo PostgreSQL no schema da Loja.
- A migração **Flyway V12** atribui códigos aos produtos já cadastrados, sem reescrever `product.code`, preços, saldos ou histórico. Novos produtos recebem o código automaticamente.
- A impressão usa **Code 128 B vetorial com checksum**, sem fonte de barcode ou serviço externo. As novas etiquetas de **prateleira (100 × 55 mm)** e **gaveta (60 × 40 mm)** têm nome, valor cadastrado, unidade, código completo e os três últimos dígitos em destaque (ex.: **FINAL 123**).
- **Três modos de identificação no mesmo campo (Estoque e Vendas):** câmera do celular/computador, leitor USB/Bluetooth no modo teclado + Enter e digitação de nome, código ou últimos dígitos.
- **Pesquisa instantânea:** digite o nome, o código ou somente os **3 números finais** (ex.: `123`). A lista sugere até 10 produtos e prioriza aqueles cujo código termina com o trecho digitado. Caso mais de um material corresponda, o funcionário deve escolher a etiqueta correta. A lista geral do Estoque também pesquisa por trechos do código interno.
- No **Estoque**, o bip mostra o nome e saldo e oferece `Entrada` ou `Saída`, abrindo o formulário existente (quantidade, motivo e confirmação).
- Em **Vendas**, cada bip adiciona o item ao carrinho em rascunho. Repetir o código aumenta sua quantidade. A baixa acontece **somente ao confirmar a venda**, seguindo as regras existentes.
- APIs autenticadas de leitura `GET /api/admin/barcodes/lookup?code=LF0000000001&locationId=<UUID>` e de etiqueta `GET /api/admin/barcodes/product/<UUID>?locationId=<UUID>`. Apenas pessoas com permissões comerciais/estoque têm acesso; a consulta valida o local da loja e nunca escreve dados.

## Como usar

1. Abra **Estoque**, procure o material desejado e clique em **Etiqueta**. Escolha **Imprimir prateleira** ou **Imprimir gaveta**, escolha a impressora ou **Salvar como PDF**, imprima e cole na caixa/gaveta.
2. No mesmo campo, **escolha a câmera**, ou clique e bipe com o leitor configurado como **teclado (HID) com sufixo Enter**, ou digite nome/código/final. Após identificar o produto, selecione **Entrada** ou **Saída** e confirme quantidade e motivo.
3. Para venda, abra **Vendas > Nova venda**, coloque o foco em **Bipar ou buscar etiqueta** e bipe ou digite parte do código para escolher o produto. Confira a quantidade (especialmente produtos vendidos por metro ou quilo), calcule total, registre pagamento e confirme como de costume.
4. Se o material for de venda, **não registre uma segunda saída manual**. A venda confirmada já desconta o estoque uma vez.

### Limites e cuidados

- Impressora: escolha o papel correto (**100 × 55 mm** ou **60 × 40 mm**), escala 100% e margens zero. Teste com o scanner antes de imprimir em lote. O sistema não instala drivers.
- **Câmera:** toque em **Abrir câmera**, permita o acesso, centralize o Code 128 e aguarde a identificação. O reconhecimento usa ZXing no próprio dispositivo; nenhuma imagem é enviada ao servidor. A página deve estar em **HTTPS**. Para fechar ou interromper, toque em **Fechar câmera** ou saia da tela. Sem permissão de câmera, use o leitor ou digite o código.
- O leitor físico precisa operar como teclado; dispositivos serial/SDK proprietário exigem integração adicional.
- Esta versão gera **códigos internos** para prateleiras. Não associa automaticamente o EAN do fabricante nem permite alterar manualmente o código interno.
- Para uma tela de **conferência de recebimento vinculada diretamente ao pedido de compra**, a leitura deverá ser integrada ao fluxo de compras, sem criar dupla entrada.
- A Vercel ainda tem latência de inicialização do backend; o leitor não a elimina. Execute os testes antes de operar comercialmente.
- Não imprimir etiquetas de itens inativos; um produto inativo não é localizado pela consulta.
- **Preço nas etiquetas:** o valor é o preço do cadastro **no momento da impressão**; se o preço mudar, a loja deve substituir a etiqueta na prateleira. O total da venda continua sendo calculado pelo backend.
- Códigos internos são únicos, mas **3 dígitos finais não são únicos** quando há mais de mil produtos. A busca sugere as correspondências; apenas o código completo bipado é identificação exata.
- Um código identifica um **tipo de material**, não cada peça individual. A quantidade é informada pelo operador, exceto repetição de bip na venda.
- Não alterar manualmente a sequência do banco nem reutilizar códigos de produtos removidos.

## Testes

O CI executa testes Java/PostgreSQL em schema isolado, teste de formato e unicidade, consulta por estoque e loja, além de testes frontend para o Code 128 e verificação de que bipar **não** gera movimentação automaticamente.

Confira a execução de pull request e homologue num ambiente de teste antes de usar a nova função nas vendas reais.


## Atualização das etiquetas antigas

O formato de impressão é único para todos os produtos: **tanto os materiais existentes quanto os novos recebem o novo visual** quando a etiqueta é aberta para impressão. O código `LF...` que já identifica cada produto permanece o mesmo, e o estoque não é movimentado.

Para substituir etiquetas físicas antigas com facilidade, em **Estoque > Materiais no estoque > Etiquetas dos materiais já cadastrados**, clique em **Preparar etiquetas da página** (até 20 produtos ativos da página de resultados) e imprima em formato de prateleira ou gaveta. Para imprimir todas as páginas, avance e repita a operação. Etiquetas já coladas nas prateleiras precisam ser reimpressas e trocadas fisicamente.

## Compatibilidade da câmera e privacidade

- A leitura utiliza `@zxing/browser` e `@zxing/library` carregados somente ao abrir a câmera, inclusive como alternativa em navegadores que não possuem `BarcodeDetector` nativo (como o Safari do iPhone). São lidos códigos de barras lineares Code 128, sem enviar frames ao backend.
- O acesso à câmera deve ser autorizado pelo usuário, pode ser recusado e exige HTTPS. Sem câmera disponível ou após negar a permissão, o campo continua aceitando leitor HID e digitação. Teste a posição do código, iluminação e foco em aparelho real.
- Na tela de vendas, o código identificado apenas adiciona ou atualiza um item no carrinho em rascunho; o estoque só muda depois de concluir a venda. No Estoque, a identificação também não altera quantidades.
