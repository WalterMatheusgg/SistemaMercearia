# Etiquetas e leitura de código de barras — Loja Ferragens

## O que foi implementado

- **Cada produto recebe automaticamente um código curto único** no formato `LF0000000001` (12 caracteres). A sequência é gerada pelo PostgreSQL no schema da Loja.
- A migração **Flyway V12** atribui códigos aos produtos já cadastrados, sem reescrever `product.code`, preços, saldos ou histórico. Novos produtos recebem o código automaticamente.
- A impressão usa **Code 128 B vetorial com checksum**, sem fonte de barcode ou serviço externo. A etiqueta de 100 × 55 mm contém nome, código e unidade e pode ser colada na prateleira.
- Leitores **USB/Bluetooth no modo teclado + Enter** identificam o material; também é possível digitar o código.
- No **Estoque**, o bip mostra o nome e saldo e oferece `Entrada` ou `Saída`, abrindo o formulário existente (quantidade, motivo e confirmação).
- Em **Vendas**, cada bip adiciona o item ao carrinho em rascunho. Repetir o código aumenta sua quantidade. A baixa acontece **somente ao confirmar a venda**, seguindo as regras existentes.
- APIs autenticadas de leitura `GET /api/admin/barcodes/lookup?code=LF0000000001&locationId=<UUID>` e de etiqueta `GET /api/admin/barcodes/product/<UUID>?locationId=<UUID>`. Apenas pessoas com permissões comerciais/estoque têm acesso; a consulta valida o local da loja e nunca escreve dados.

## Como usar

1. Abra **Estoque**, procure o material desejado e clique em **Etiqueta**. Clique em **Imprimir etiqueta de prateleira**, escolha a impressora ou **Salvar como PDF**, imprima e cole na caixa/gaveta.
2. Com o leitor configurado no modo **teclado (HID) com sufixo Enter**, clique em **Código da etiqueta**, bipar, e veja o material identificado. Selecione **Entrada** ou **Saída**, informe quantidade e motivo, confirme.
3. Para venda, abra **Vendas > Nova venda**, coloque o foco em **Bipar etiqueta da prateleira** e bipar. Confira a quantidade (especialmente produtos vendidos por metro ou quilo), calcule total, registre pagamento e confirme como de costume.
4. Se o material for de venda, **não registre uma segunda saída manual**. A venda confirmada já desconta o estoque uma vez.

### Limites e cuidados

- Impressora: a etiqueta é uma página otimizada para 100 × 55 mm; confirme orientação, margens e leitura no seu equipamento. O sistema não instala drivers.
- O leitor precisa operar como teclado; dispositivos serial/SDK proprietário exigem integração adicional. Câmera de celular não faz parte desta versão.
- Esta versão gera **códigos internos** para prateleiras. Não associa automaticamente o EAN do fabricante nem permite alterar manualmente o código interno.
- Para uma tela de **conferência de recebimento vinculada diretamente ao pedido de compra**, a leitura deverá ser integrada ao fluxo de compras, sem criar dupla entrada.
- A Vercel ainda tem latência de inicialização do backend; o leitor não a elimina. Execute os testes antes de operar comercialmente.
- Não imprimir etiquetas de itens inativos; um produto inativo não é localizado pela consulta.
- Um código identifica um **tipo de material**, não cada peça individual. A quantidade é informada pelo operador, exceto repetição de bip na venda.
- Não alterar manualmente a sequência do banco nem reutilizar códigos de produtos removidos.

## Testes

O CI executa testes Java/PostgreSQL em schema isolado, teste de formato e unicidade, consulta por estoque e loja, além de testes frontend para o Code 128 e verificação de que bipar **não** gera movimentação automaticamente.

Confira a execução de pull request e homologue num ambiente de teste antes de usar a nova função nas vendas reais.
