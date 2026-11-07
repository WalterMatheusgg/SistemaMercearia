# 🎨 Frontend — Loja Ferragens

Interface administrativa da **Loja Ferragens**, desenvolvida em **Angular 22**, **TypeScript 6**, **Angular Material** e **SCSS**.

Este diretório integra o sistema Full Stack do repositório principal. O frontend não é um checkout público: as telas de produtos, estoque, vendas, caixa, financeiro, clientes e equipe exigem autenticação e respeitam as permissões retornadas pelo backend.

## 🧩 Organização

| Área | Responsabilidade |
|:--|:--|
| `app/auth/` | Login, recuperação e sessão |
| `app/admin/` | Layout, menu, navegação e atalhos |
| `app/products/` | Cadastro, filtros e imagens |
| `app/inventory/` | Saldos, movimentações e histórico |
| `app/commerce/` | Vendas, compras, clientes, caixa e financeiro |
| `e2e/` | Cenários de navegador com Playwright |

## 🚀 Desenvolvimento

**Requisitos:** Node.js 24 e npm 11. O backend Spring Boot deve estar ativo para chamadas à API.

```bash
cd frontend
npm ci
npm start -- --host 0.0.0.0
```

Abra **http://localhost:4200**. O proxy em `proxy.conf.json` encaminha a API para o backend local na porta 8080. No Codespaces, utilize a aba **Ports**.

Para executar o projeto inteiro pelo Docker, prefira o script da raiz:

```bash
bash scripts/start.sh
```

## 🧪 Qualidade e build

```bash
npm test -- --watch=false
npm run build
```

Os testes E2E com **Playwright** precisam da aplicação completa e de um **banco isolado para testes**; não use o ambiente de produção:

```bash
npm run e2e
```

O GitHub Actions executa esses cenários com PostgreSQL e serviços de teste, em desktop e mobile.

## 📱 Responsividade e acessibilidade

- Navegação desktop com menu lateral recolhível e gaveta no celular.
- Estoque com grade ou lista, fotos em modo `contain` e botões acessíveis ao toque.
- Testes de largura para 320, 390, 600, 760 e 1280 px.
- Feedback de erros, navegação por teclado e confirmação antes de alterações comerciais.

## 🔐 Autenticação

Cookies HttpOnly, CSRF e autorização são controlados pelo **Spring Boot**, não pelo Angular isoladamente. O backend também serve a versão compilada da interface no deploy Docker. Não coloque senhas ou chaves privadas em arquivos `environment.ts` ou no código publicado.

[README principal](../README.md) · [Arquitetura](../docs/ARQUITETURA.md) · [Guia de operação](../docs/OPERACAO.md)
