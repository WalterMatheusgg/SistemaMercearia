# Loja — Interface e navegação

A plataforma utiliza somente o nome **Loja** e opera como painel interno de ferragens, sem vitrine pública nem carrinho no fluxo principal. O título da aba do navegador identifica o módulo, como `Estoque | Loja`, `Caixa | Loja` e `Minha conta | Loja`.

## Atualizações

- Logotipo com inicial L e favicon SVG próprio; idioma do documento `pt-BR`.
- Painel administrativo com atalhos por permissão, fluxo de atendimento e menu lateral organizado.
- Navegação responsiva com menu horizontal rolável no celular, indicador de módulo e link para pular direto ao conteúdo.
- Vitrine com cards melhorados, mensagem explícita para produto sem saldo, estado de carregamento e prevenção de respostas fora de ordem durante buscas.
- Login, botões, tabelas, formulários e alertas com cores, bordas e hierarquia visual consistentes.
- Rótulos, navegação e foco continuam acessíveis; animações são reduzidas conforme a preferência do usuário.

## Cadastro e operação

No cadastro de produtos e contatos (clientes e fornecedores), **nenhum campo é obrigatório**. Se não informar nome, o sistema cria um nome provisório editável; produtos recebem categoria e unidade padrão e preços zerados. Os campos informados continuam sendo validados quanto a formato e limites.

O novo formulário de produtos não pede código. O backend gera um identificador técnico estável usado somente para integridade dos vínculos e histórico. Nome, categoria, unidade e preço de referência são campos de cadastro rápido; custo e observações ficam em opções adicionais. Produtos podem ser inativados e reativados. A edição não modifica códigos técnicos.

O painel apresenta indicadores reais de produtos ativos, itens para reposição no local principal, vendas e valor confirmado no dia; cada cartão respeita as permissões. Busca e filtros permitem navegar sem menus extensos. As animações de fundo têm controle de pausa.

A API histórica de clientes fica desabilitada por padrão com `CUSTOMER_PORTAL_ENABLED=false`, preservando os registros no PostgreSQL. A estrutura de checkout não foi apagada do banco.

Movimentações de estoque, lançamentos financeiros e credenciais de acesso ainda exigem dados mínimos necessários para proteger o histórico, os saldos e as pessoas. Não é seguro executar uma operação financeira ou de estoque totalmente vazia.

## Senhas

A regra solicitada é mínimo de **4 caracteres e 1 número** (máximo de 72 bytes), incluindo cadastro interno, alteração e recuperação. Essa regra é mais fraca que o padrão recomendado: para a conta do dono, prefira uma senha longa e única.

## Credenciais administrativas

O usuário inicial é `admin` e sua senha inicial forte é gerada e guardada **somente** no arquivo `.env` privado do Codespace. Consulte apenas no próprio terminal:

```bash
bash scripts/admin-access.sh
```

**Não use `admin/admin`**: o projeto é destinado à operação real, e senhas de quatro caracteres não atendem à validação mínima do backend. O bootstrap não redefine senhas já gravadas no PostgreSQL. Para trocar a senha de um usuário existente, use a área **Usuários** com uma conta de dono ou a recuperação por e-mail verificado.

### Criar ou recuperar o dono com senha temporária

No terminal **privado** do Codespace (não em produção), execute:

```bash
cd /workspaces/SistemaMercearia
git pull --ff-only origin main
python3 scripts/owner-setup.py
```

Digite `CONFIRMAR`. O procedimento confere que está usando o Compose `local`, faz backup automático, cria o usuário `admin` com `ROLE_OWNER` se não existir ou redefine a senha se essa conta já for dona. A nova senha aleatória aparece **uma única vez** no terminal, não no Git nem no `.env`. Se `admin` pertencer a outro papel, o procedimento é bloqueado sem promovê-lo.

Depois de entrar na porta 8080, abra **Usuários → Editar admin → Nova senha** para substituir a temporária. O procedimento invalida sessões anteriores da conta admin e registra a recuperação no histórico administrativo. Não execute novamente se não quiser trocar a senha outra vez.

## Preservação

Nenhuma migração histórica do Flyway, saldo de estoque, pedido, conta, senha ou volume do PostgreSQL foi alterado por esta refatoração. O nome da loja comercial permanece configurável em **Loja e auditoria**, independentemente do nome do software.

Para colocar a operação na internet, consulte [HOSPEDAGEM.md](HOSPEDAGEM.md). Pagamentos e emissão fiscal automáticos ainda exigem integrações externas.
