import { test, expect, Page, BrowserContext } from '@playwright/test';
import { randomUUID } from 'node:crypto';
const admin = process.env['E2E_ADMIN_USER'] ?? 'admin';
const password = process.env['E2E_ADMIN_PASSWORD'] ?? 'local-e2e-password-123456';
const mailpit = process.env['E2E_MAILPIT_URL'] ?? 'http://localhost:8025';
async function login(page: Page, user = admin, pass = password) {
  await page.goto('/login');
  await page.getByLabel('Usuário', { exact: true }).fill(user);
  await page.getByLabel('Senha', { exact: true }).fill(pass);
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page).toHaveURL(/\/(admin\/home|account)/);
}
async function command(context: BrowserContext, path: string, body: unknown, method = 'POST') {
  await context.request.get('/api/auth/csrf');
  const cookie = (await context.cookies()).find((c) => c.name === 'XSRF-TOKEN');
  const response = await context.request.fetch(path, {
    method,
    data: body,
    headers: {
      'X-XSRF-TOKEN': decodeURIComponent(cookie?.value ?? ''),
      'Idempotency-Key': randomUUID(),
    },
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.status() === 204 ? {} : response.json();
}
async function seed(context: BrowserContext) {
  const code = 'E2E-' + randomUUID().slice(0, 12);
  const p = await command(context, '/api/admin/products', {
    code,
    name: 'Material ' + code,
    description: 'Material para teste completo',
    categoryId: '00000000-0000-0000-0000-000000000104',
    baseUnitCode: 'UN',
    cost: '1.00',
    price: '2.00',
  });
  await command(context, '/api/admin/inventory/movements', {
    productId: p.id,
    locationId: '00000000-0000-0000-0000-000000000201',
    kind: 'INITIAL',
    quantity: '50',
    reason: 'Contagem de teste',
  });
  return { ...p, code };
}
async function mailLink(page: Page, email: string, route: string) {
  let id = '';
  await expect
    .poll(
      async () => {
        const response = await page.request.get(mailpit + '/api/v1/messages');
        const data = await response.json();
        const found = (data.messages ?? data.Messages ?? []).find(
          (m: any) =>
            (m.To ?? m.to ?? []).some((to: any) => (to.Address ?? to.address) === email) &&
            (route === 'verify-email'
              ? /Verificar/.test(m.Subject ?? m.subject)
              : /Recuperar/.test(m.Subject ?? m.subject)),
        );
        id = found?.ID ?? found?.id ?? '';
        return Boolean(id);
      },
      { timeout: 35000, intervals: [1000, 2000] },
    )
    .toBeTruthy();
  const response = await page.request.get(mailpit + '/view/' + id + '.txt');
  const text = await response.text();
  const url = text.match(new RegExp('https?://[^\\s]+/' + route + '\\?token=[A-Za-z0-9_-]+'))?.[0];
  expect(url).toBeTruthy();
  return url!;
}

test('venda pelo navegador baixa de 50 para 35 e aparece no estoque', async ({ page, context }) => {
  await login(page);
  const product = await seed(context);
  const current = await (await context.request.get('/api/admin/cash')).json();
  if (!current.current.id) await command(context, '/api/admin/cash/open', { amount: '100.00' });
  await page.goto('/admin/sales');
  await page.getByRole('button', { name: 'Nova venda / orçamento' }).click();
  await page.getByLabel('Buscar produto', { exact: true }).fill(product.name);
  await page.getByRole('button', { name: 'Buscar produto', exact: true }).click();
  await expect(
    page.getByLabel('Produto', { exact: true }).locator('option[value="' + product.id + '"]'),
  ).toHaveCount(1);
  await page.getByLabel('Produto', { exact: true }).selectOption(product.id);
  await page.getByLabel('Quantidade', { exact: true }).fill('15');
  await page.getByRole('button', { name: 'Adicionar item', exact: true }).click();
  await page.getByRole('button', { name: 'Calcular valores' }).click();
  await expect(page.getByText('Total calculado pelo sistema')).toBeVisible();
  await page.getByRole('button', { name: 'Adicionar recebimento' }).click();
  await page.getByRole('button', { name: 'Concluir venda e baixar estoque' }).click();
  await expect(page.getByRole('status')).toContainText('Operação confirmada');
  await page.goto('/admin/inventory');
  await page.getByLabel('Nome do produto').fill(product.name);
  await page.getByRole('button', { name: 'Buscar', exact: true }).click();
  const row = page.locator('tbody tr').filter({ hasText: product.name });
  await expect(row.first()).toContainText('35');
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 2),
  ).toBeTruthy();
});

test('recuperação usa link recebido no Mailpit e invalida a senha antiga', async ({
  page,
  context,
}) => {
  const username = 'reset-' + randomUUID().slice(0, 8),
    email = username + '@ferragens.local';
  await login(page);
  await command(context, '/api/admin/users', {
    username, email, password, roles: ['STOCKKEEPER'], enabled: true,
  });
  await page.getByRole('button', { name: 'Sair', exact: true }).click();
  await page.goto('/forgot-password');
  await page.getByLabel('Usuário ou e-mail').fill(username);
  await page.getByRole('button', { name: 'Enviar instruções' }).click();
  await expect(page.getByRole('status')).toContainText('Se houver conta ativa');
  const link = await mailLink(page, email, 'reset-password');
  await page.goto(link);
  await page.getByLabel('Senha (mínimo 4 caracteres e 1 número)', { exact: true }).fill(password + '-nova');
  await page.getByLabel('Confirmar senha').fill(password + '-nova');
  await page.getByRole('button', { name: 'Salvar nova senha' }).click();
  await expect(page.getByRole('status')).toContainText('Senha atualizada');
  await login(page, username, password + '-nova');
});

test('estoquista não acessa financeiro nem gestão de usuários', async ({ page, context }) => {
  await login(page);
  const username = 'stock-' + randomUUID().slice(0, 8);
  await command(context, '/api/admin/users', {
    username,
    email: username + '@ferragens.local',
    password,
    roles: ['STOCKKEEPER'],
    enabled: true,
  });
  const supplier = await command(context, '/api/admin/contacts/SUPPLIER', {
    name: 'Fornecedor ' + username,
    active: true,
  });
  await command(context, '/api/admin/finance', {
    direction: 'PAYABLE',
    description: 'Conta interna de teste',
    partyId: supplier.id,
    amount: '10.00',
    dueDate: new Date().toISOString().slice(0, 10),
  });
  await page.getByRole('button', { name: 'Sair', exact: true }).click();
  await login(page, username);
  const supplierView = await (
    await context.request.get('/api/admin/contacts/SUPPLIER/' + supplier.id)
  ).json();
  expect(supplierView).not.toHaveProperty('accounts');
  await expect(page.getByRole('link', { name: 'Financeiro', exact: true })).toHaveCount(0);
  expect((await context.request.get('/api/admin/finance')).status()).toBe(403);
  expect((await context.request.get('/api/admin/users')).status()).toBe(403);
  await page.goto('/admin/finance');
  await expect(page).toHaveURL(/\/admin\/home/);
});

test('controle abre no login, identifica cada aba e oculta vitrine', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/login/);
  await expect(page).toHaveTitle('Entrar | Loja');
  await expect(page.getByRole('link', { name: 'Criar conta de cliente' })).toHaveCount(0);
  await page.goto('/store');
  await expect(page).toHaveURL(/\/login/);
  await login(page);
  await expect(page).toHaveTitle('Painel | Loja');
  await expect(page.getByRole('link', { name: 'Produtos', exact: true })).toBeVisible();
  await page.goto('/admin/products');
  await expect(page).toHaveTitle('Produtos | Loja');
  await expect(page.getByText('Seus produtos,')).toBeVisible();
  await expect(page.getByText('Código', { exact: true })).toHaveCount(0);
  await page.goto('/admin/inventory');
  await expect(page).toHaveTitle('Estoque | Loja');
  await page.goto('/admin/sales');
  await expect(page).toHaveTitle('Vendas e pedidos | Loja');
});

test('cadastro rápido permite criar e editar sem código manual', async ({ page }) => {
  await login(page);
  await page.goto('/admin/products');
  const name = 'Parafuso controle ' + randomUUID().slice(0, 8);
  await page.getByRole('button', { name: /Novo produto/ }).click();
  const editor = page.getByRole('dialog', { name: 'Novo produto' });
  await expect(editor.getByLabel('Código')).toHaveCount(0);
  await editor.getByLabel('Nome do produto').fill(name);
  await editor.getByLabel('Preço de referência (R$)').fill('9,50');
  await editor.getByRole('button', { name: 'Cadastrar produto' }).click();
  await expect(page.locator('.success-notice')).toContainText('Produto cadastrado');
  const search = page.getByRole('searchbox', { name: 'Buscar produto' });
  await search.fill(name);
  const row = page.locator('tr').filter({ hasText: name });
  await expect(row.first()).toBeVisible();
  await row.getByRole('button', { name: 'Editar' }).click();
  await page.getByRole('dialog').getByLabel('Preço de referência (R$)').fill('11,90');
  await page.getByRole('dialog').getByRole('button', { name: 'Salvar alterações' }).click();
  await expect(row.first()).toContainText('R$ 11,90');
});
