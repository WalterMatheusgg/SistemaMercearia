# Loja — Interface e navegação

A plataforma utiliza somente o nome **Loja**. O título da aba do navegador identifica o módulo, como `Estoque | Loja`, `Caixa | Loja` e `Minha conta | Loja`.

## Atualizações

- Logotipo com inicial L e favicon SVG próprio; idioma do documento `pt-BR`.
- Painel administrativo com atalhos por permissão, fluxo de atendimento e menu lateral organizado.
- Navegação responsiva com menu horizontal rolável no celular, indicador de módulo e link para pular direto ao conteúdo.
- Vitrine com cards melhorados, mensagem explícita para produto sem saldo, estado de carregamento e prevenção de respostas fora de ordem durante buscas.
- Login, botões, tabelas, formulários e alertas com cores, bordas e hierarquia visual consistentes.
- Rótulos, navegação e foco continuam acessíveis; animações são reduzidas conforme a preferência do usuário.

## Credenciais administrativas

O usuário inicial é `admin` e sua senha inicial forte é gerada e guardada **somente** no arquivo `.env` privado do Codespace. Consulte apenas no próprio terminal:

```bash
bash scripts/admin-access.sh
```

**Não use `admin/admin`**: o projeto é destinado à operação real, e senhas de quatro caracteres não atendem à validação mínima do backend. O bootstrap não redefine senhas já gravadas no PostgreSQL. Para trocar a senha de um usuário existente, use a área **Usuários** com uma conta de dono ou a recuperação por e-mail verificado.

## Preservação

Nenhuma migração histórica do Flyway, saldo de estoque, pedido, conta, senha ou volume do PostgreSQL foi alterado por esta refatoração. O nome da loja comercial permanece configurável em **Loja e auditoria**, independentemente do nome do software.

Para colocar a operação na internet, consulte [HOSPEDAGEM.md](HOSPEDAGEM.md). Pagamentos e emissão fiscal automáticos ainda exigem integrações externas.
