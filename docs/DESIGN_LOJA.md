# Loja — Interface e navegação

A plataforma utiliza somente o nome **Loja** e opera como painel interno de ferragens, sem vitrine pública nem carrinho no fluxo principal. O título da aba do navegador identifica o módulo, como `Estoque | Loja`, `Financeiro | Loja` e `Produtos | Loja`.

## Atualizações

- Logotipo com inicial L e favicon SVG próprio; idioma do documento `pt-BR`.
- Painel administrativo com atalhos por permissão, fluxo de atendimento e menu lateral organizado.
- Navegação responsiva com menu horizontal rolável no celular, indicador de módulo e link para pular direto ao conteúdo.
- A vitrine pública deixou de fazer parte do fluxo interno. O aplicativo abre no painel de controle após o login.
- Login, botões, tabelas, formulários e alertas com cores, bordas e hierarquia visual consistentes.
- Rótulos, navegação e foco continuam acessíveis; animações são reduzidas conforme a preferência do usuário.

## Cadastro e operação

Nos contatos (clientes e fornecedores), nenhum campo de identificação é obrigatório. No cadastro manual de produtos, **a foto é obrigatória** (câmera ou arquivo, JPG/PNG/WebP até 3 MB); os demais campos são opcionais. Se não informar nome, o sistema cria um nome provisório editável; produtos recebem categoria e unidade padrão e preços zerados. Os campos informados continuam sendo validados quanto a formato e limites.

O novo formulário de produtos não pede código. O backend gera um identificador técnico estável usado somente para integridade dos vínculos e histórico. Nome, categoria, unidade e preço de referência são campos de cadastro rápido; custo e observações ficam em opções adicionais. Produtos podem ser inativados e reativados. A edição não modifica códigos técnicos.

O painel apresenta indicadores reais de produtos ativos, itens para reposição no local principal, vendas e valor confirmado no dia; cada cartão respeita as permissões. O menu lateral pode ser recolhido no computador e abre como gaveta no celular. A busca Ctrl+K permite chegar rapidamente aos módulos.

A API histórica de clientes fica desabilitada por padrão com `CUSTOMER_PORTAL_ENABLED=false`, preservando os registros no PostgreSQL. A estrutura de checkout não foi apagada do banco.

Movimentações de estoque, lançamentos financeiros e credenciais de acesso ainda exigem dados mínimos necessários para proteger o histórico, os saldos e as pessoas. Não é seguro executar uma operação financeira ou de estoque totalmente vazia.

## Senhas

A regra solicitada é mínimo de **4 caracteres e 1 número** (máximo de 72 bytes), incluindo cadastro interno, alteração e recuperação. Essa regra é mais fraca que o padrão recomendado: para a conta do dono, prefira uma senha longa e única.

## Credenciais administrativas

O usuário inicial é `admin` e sua senha inicial forte é gerada e guardada **somente** no arquivo `.env` privado do Codespace. Consulte apenas no próprio terminal:

```bash
bash scripts/admin-access.sh
```

**Não use `admin/admin`**: mesmo sendo permitidos quatro caracteres com pelo menos um número, senhas curtas não são adequadas para o dono de uma operação real. O bootstrap não redefine senhas já gravadas no PostgreSQL. Para trocar a senha de um usuário existente, use a área **Usuários** com uma conta de dono ou a recuperação por e-mail verificado.

### Criar ou recuperar o dono com senha temporária

No terminal **privado** do Codespace (não em produção), execute:

```bash
cd /workspaces/SistemaMercearia
git pull --ff-only origin main
python3 scripts/owner-setup.py
```

Digite `CONFIRMAR`. O procedimento confere que está usando o Compose `local`, faz backup automático, cria o usuário `admin` com `ROLE_OWNER` se não existir ou redefine a senha se essa conta já for dona. A nova senha aleatória aparece **uma única vez** no terminal, não no Git nem no `.env`. Se `admin` pertencer a outro papel, o procedimento é bloqueado sem promovê-lo.

Depois de entrar na porta 8080, abra **Usuários → Editar admin → Nova senha** para substituir a temporária. O procedimento invalida sessões anteriores da conta admin e registra a recuperação no histórico administrativo. Não execute novamente se não quiser trocar a senha outra vez.

## Visual e experiência — refatoração de novembro/2026

- Cabeçalho próprio com cores escuras, verde natural e acentos claros, inspirado nos padrões de interação e dados conectados da SEA, sem replicar o design institucional.
- Comando global `Ctrl+K` / `⌘K` para encontrar módulos rapidamente, com pesquisa por tarefa e permissões.
- Painel com elemento conectado animado discreto, opção de pausar movimento, indicador de atenção real e atalhos operacionais; sem dados simulados.
- Atendimento em três etapas, com detalhes opcionais recolhidos e visibilidade do total antes de finalizar.
- Dinheiro registrado automaticamente em sessão técnica ao movimentar: **sem precisar abrir ou fechar caixa** na rotina.
- Financeiro concentra contas e pagamentos e o registro de dinheiro físico em abas da mesma página; relatórios têm períodos Hoje, 7 dias, Este mês, datas livres e visualização responsiva.
- Assistente consulta dados reais restritos ao usuário, recomenda melhorias, exibe falhas internas HTTP 5xx para gestores e permite importar até 200 itens com prévia, confirmação e auditoria. Os exemplos pré-carregáveis têm o prefixo TESTE. Para IA externa, é necessário configurar a chave e optar explicitamente pelo envio de contexto autorizado.

## Preservação

Nenhuma migração histórica do Flyway, saldo de estoque, pedido, conta, senha ou volume do PostgreSQL foi alterado por esta refatoração. O nome da loja comercial permanece configurável em **Loja e auditoria**, independentemente do nome do software.

Para colocar a operação na internet, consulte [HOSPEDAGEM.md](HOSPEDAGEM.md). Pagamentos e emissão fiscal automáticos ainda exigem integrações externas.

## Fluxo de fotos e quantidade inicial

O cadastro manual cria um produto e sua foto em uma única transação, registrando a quantidade inicial no livro de movimentações quando informada. Se uma das etapas falhar, não há cadastro parcial. Fotos são armazenadas no PostgreSQL em tabela própria, protegidas pela autenticação; SVG, URL externa e arquivo maior que 3 MB não são aceitos. A busca do Google abre uma pesquisa externa: escolha e salve apenas imagens que tenha permissão para usar. Materiais importados anteriormente ou por lote podem não ter foto e exibem uma ilustração até a atualização.

Não é possível fazer o assistente detectar todos os problemas do navegador sem um sistema de telemetria adicional. Os avisos cobrem respostas HTTP 5xx do backend e não enviam alertas por e-mail automaticamente.
