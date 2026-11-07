# Executar no Codespace

## Sincronização segura com o GitHub

Se já existe banco Docker no Codespace, execute no terminal privado:

```bash
cd /workspaces/SistemaMercearia
git status --short
bash scripts/backup.sh
git switch main
git pull --ff-only origin main
bash scripts/sync-codespace.sh
```

O primeiro pull baixa o novo script, sem aplicar migrações. O script faz outro backup antes de compilar/iniciar, recusa mudanças locais ou branches divergentes, executa testes e um smoke test não destrutivo. Sem `.env` no ambiente novo, omita o backup inicial. Preserve uma cópia do dump fora do Codespace. Não use `reset --hard`, `git clean`, `down -v` ou `docker volume prune` para sincronizar.

O caminho mais simples usa Docker para compilar o Angular, empacotar o backend e servir tudo na porta **8080**, na mesma origem. Não é preciso instalar Java/Node no Codespace para essa opção.

```bash
cd /workspaces/SistemaMercearia
git fetch origin
git switch main
git pull --ff-only origin main
bash scripts/start.sh
```

A `main` é a branch definitiva do projeto. Guarde alterações locais antes de atualizar; não use reset/clean para atualizar.

O script preserva `.env` e volumes existentes. Se `.env` não existir, cria senhas aleatórias; consulte `BOOTSTRAP_ADMIN_USERNAME` e `BOOTSTRAP_ADMIN_PASSWORD` nesse arquivo para entrar. Senhas não são exibidas nos logs nem enviadas ao GitHub. Usuários existentes mantêm suas senhas; mudar o bootstrap não troca a senha gravada no banco.

Na aba **Ports**, abra **8080 → Open in Browser**. O site abre no catálogo; **Entrar** dá acesso à gestão ou à conta do cliente. Para o Mailpit, abra a porta **8025**. Mantenha essas portas privadas durante o desenvolvimento.

O script acrescenta `APP_PUBLIC_URL` se estiver ausente. No Codespace, usa o endereço encaminhado da porta 8080 para que os links de verificação/recuperação abram corretamente. Confira essa variável ao reutilizar um `.env` antigo ou trocar de Codespace. Reinicie o container depois de mudar o endereço:

```bash
docker compose --profile app up -d --force-recreate app
```

Não rode o antigo backend na mesma porta 8080 ao usar o container completo. O fluxo de desenvolvimento com Maven em 8080 e Angular em 4200 continua descrito no README; nesse caso, configure `APP_PUBLIC_URL` com o endereço do Angular.

## Primeira configuração

1. Entre como dono. Em **Usuários**, cadastre seu e-mail e contas individuais dos funcionários. Alterações invalidam sessões antigas; entre novamente após editar seu próprio acesso.
2. Em **Loja e auditoria**, configure nome, telefone, endereço, taxa de entrega, validade das reservas e instruções verdadeiras de pagamento.
3. Cadastre categorias/unidades quando necessário, produtos e clientes/fornecedores. Unidades utilizadas ficam preservadas.
4. Em **Estoque**, registre o saldo inicial real de cada produto e seu mínimo.
5. Em **Caixa**, confira o fundo em dinheiro e abra a sessão. Depois use **Vendas e pedidos** para concluir uma venda e verificar estoque, histórico e conta vinculada.
6. Para testar a loja virtual, saia da conta administrativa, crie uma conta de cliente, abra a mensagem de verificação no Mailpit e confirme o e-mail. Monte o carrinho, calcule e envie o pedido.
7. Volte como funcionário autorizado, confira o pagamento fora do site e confirme o pedido. Prepare e conclua a retirada/entrega.
8. Em **Alertas por e-mail**, verifique o e-mail do administrador e selecione os eventos. As mensagens locais ficam no Mailpit; não são enviadas a destinatários externos.

## Atualizar e verificar

```bash
git pull --ff-only origin main
bash scripts/start.sh
docker compose --profile app ps
docker compose --profile app logs --tail=100 app
bash scripts/smoke.sh
```

A verificação `smoke.sh` **somente lê** saúde, página inicial, catálogo, CSRF e proteção da API, sem criar produtos ou vendas. Se a imagem compilar mas o Docker falhar com `RWLayer ... unexpectedly nil`, preserve o volume: `docker compose --profile app down` (sem `-v`) e depois `docker compose --profile app up -d --no-build`. Se persistir, reinicie o Codespace normalmente e tente novamente. Não use **Full Rebuild** nem `docker volume prune` como primeira medida.

Flyway aplica as migrações pendentes sem apagar os cadastros. Faça backup antes de atualizar um banco que contenha dados importantes. Nunca altere uma migração já aplicada.

Se aparecer falha de autenticação no PostgreSQL, confira a senha original do volume e as variáveis `POSTGRES_PASSWORD`/`DB_PASSWORD`. Alterar `.env` não troca a senha existente do banco. Não apague o volume para corrigir credenciais.

## Backup e teste de restauração

```bash
bash scripts/backup.sh
bash scripts/restore-test.sh backups/SEU_ARQUIVO.dump ferragens_restore_teste
```

O segundo comando exige um nome de banco novo e recusa o banco em uso. Não sobrescreve dados da loja. Se o banco de teste já existir, escolha outro nome. Os dumps ficam fora do Git: guarde cópias protegidas fora do Codespace e defina retenção. O Codespace pode ser excluído e não deve ser o único local de armazenamento.

## Testes no modo de desenvolvimento

Com JDK 21, Node conforme `frontend/.nvmrc` e Docker:

```bash
./mvnw verify
npm --prefix frontend ci
npm --prefix frontend test -- --watch=false
npm --prefix frontend run build
cd frontend
npx playwright install --with-deps chromium
npm run e2e
```

Os testes de navegador exigem uma aplicação local **dedicada a testes**, com usuário/senha correspondentes a `E2E_ADMIN_USER`/`E2E_ADMIN_PASSWORD`. Eles criam produtos, contas e vendas. O CI inicia seu próprio ambiente isolado, testa desktop/celular, captura e-mail local e verifica a restauração de backup.

## Primeiro acesso ou recuperação do dono

Se não consegue entrar, não apague o banco. No terminal privado do Codespace, execute `python3 scripts/owner-setup.py`. Depois de confirmar, o utilitário executa backup, cria ou recupera a conta `admin` com papel de dono e mostra uma senha temporária forte. Entre na porta 8080 e altere a senha em **Usuários**. Não funciona contra um ambiente `prod` e nunca usa `admin/admin`.
