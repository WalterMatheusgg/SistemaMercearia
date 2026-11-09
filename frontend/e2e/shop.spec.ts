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
  await page.goto('/admin/sales');
  await page.getByRole('button', { name: /Nova venda ou orçamento/ }).click();
  await page.getByLabel('Nome do material', { exact: true }).fill(product.name);
  await page.getByRole('button', { name: 'Buscar', exact: true }).click();
  await expect(
    page.getByLabel('Produto', { exact: true }).locator('option[value="' + product.id + '"]'),
  ).toHaveCount(1);
  await page.getByLabel('Produto', { exact: true }).selectOption(product.id);
  await page.getByLabel('Quantidade', { exact: true }).fill('15');
  await page.getByRole('button', { name: '+ Adicionar', exact: true }).click();
  await page.getByRole('button', { name: 'Calcular total' }).click();
  await expect(page.getByText('Total conferido')).toBeVisible();
  await page.getByRole('button', { name: '+ Registrar um recebimento' }).click();
  await page.getByRole('button', { name: 'Concluir venda' }).click();
  await expect(page.getByRole('status')).toContainText('Operação confirmada');
  await page.goto('/admin/inventory');
  await expect(page.getByRole('button', { name: 'Abrir câmera' })).toBeVisible();
  await page.getByLabel('Nome, código ou final da etiqueta').fill(product.name);
  await page.getByRole('button', { name: 'Buscar', exact: true }).click();
  const card = page.locator('.stock-card').filter({ hasText: product.name });
  await expect(card).toContainText('35');
  await expect(card).toContainText(product.name);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 2),
  ).toBeTruthy();
});

test('reimpressão das etiquetas existentes prepara lote sem movimentar estoque', async ({ page, context }) => {
  await login(page);
  const product = await seed(context);
  const itemUrl = '/api/admin/barcodes/product/' + product.id;
  const beforeResponse = await context.request.get(itemUrl);
  expect(beforeResponse.ok()).toBeTruthy();
  const before = await beforeResponse.json();
  await page.goto('/admin/inventory');
  await page.getByLabel('Nome, código ou final da etiqueta').fill(product.name);
  await page.getByRole('button', { name: 'Buscar', exact: true }).click();
  await expect(page.locator('.stock-card').filter({ hasText: product.name })).toBeVisible();
  await page.getByRole('button', { name: 'Preparar etiquetas da página' }).click();
  await expect(page.locator('.barcode-batch-ready')).toContainText('1 etiqueta(s) prontas');
  await expect(page.locator('.barcode-batch-ready').getByRole('button', { name: 'Imprimir', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Imprimir gaveta' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Imprimir prateleira' })).toHaveCount(0);
  const afterResponse = await context.request.get(itemUrl);
  expect(afterResponse.ok()).toBeTruthy();
  const after = await afterResponse.json();
  expect(after.barcode).toBe(before.barcode);
  expect(after.physical).toBe(before.physical);
  expect(after.available).toBe(before.available);
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
  if ((page.viewportSize()?.width ?? 1200) <= 760) {
    await page.getByRole('button', { name: 'Abrir ou recolher menu' }).click();
  }
  await expect(page.getByRole('link', { name: 'Produtos', exact: true })).toBeVisible();
  await page.goto('/admin/products');
  await expect(page).toHaveTitle('Produtos | Loja');
  await expect(page.getByRole('heading', { name: 'Produtos' })).toBeVisible();
  await expect(page.getByText('Código', { exact: true })).toHaveCount(0);
  await page.goto('/admin/inventory');
  await expect(page).toHaveTitle('Estoque | Loja');
  await page.goto('/admin/sales');
  await expect(page).toHaveTitle('Vendas e pedidos | Loja');
});


async function uploadDemoPhoto(dialog: import('@playwright/test').Locator) {
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4//8/AwAI/AL+XoSMAAAAAElFTkSuQmCC', 'base64');
  await dialog.locator('input[type="file"][aria-label="Selecionar foto do dispositivo"]')
    .setInputFiles({ name: 'foto-teste.png', mimeType: 'image/png', buffer: png });
}

test('cadastro rápido permite criar e editar sem código manual', async ({ page }) => {
  await login(page);
  await page.goto('/admin/products');
  const name = 'Parafuso controle ' + randomUUID().slice(0, 8);
  await page.getByRole('button', { name: /Novo produto/ }).click();
  const editor = page.getByRole('dialog', { name: 'Novo produto' });
  await expect(editor.getByLabel('Código')).toHaveCount(0);
  await editor.getByLabel('Nome do produto (opcional)').fill(name);
  await editor.getByLabel('Preço de referência (R$) — opcional').fill('9,50');
  await editor.getByLabel('Quantidade inicial em estoque (opcional)').fill('8');
  await uploadDemoPhoto(editor);
  const createResponsePromise = page.waitForResponse(res =>
    res.url().includes('/api/admin/products/complete') && res.request().method() === 'POST',
    {timeout: 12000});
  await editor.getByRole('button', { name: 'Cadastrar produto' }).click();
  const createResponse = await createResponsePromise;
  // O navegador pode descartar o corpo da resposta ao atualizar a interface.
  // Verifique HTTP sem depender de Network.getResponseBody.
  expect(createResponse.status(), 'Cadastro completo deve retornar HTTP 201').toBe(201);
  await expect(page.locator('.success-notice')).toContainText('Produto cadastrado');
  const search = page.getByRole('searchbox', { name: 'Buscar produto' });
  await search.fill(name);
  const row = page.locator('tr').filter({ hasText: name });
  await expect(row.first()).toBeVisible();
  await row.getByRole('button', { name: 'Editar' }).click();
  await page.getByRole('dialog').getByLabel('Preço de referência (R$) — opcional').fill('11,90');
  await page.getByRole('dialog').getByRole('button', { name: 'Salvar alterações' }).click();
  await expect(row.first()).toContainText('R$ 11,90');
  const img = row.locator('app-product-photo img');
  await expect(img).toBeVisible();
  const src = await img.getAttribute('src');
  expect(src).toContain('/api/admin/products/');
  const photoResponse = await page.request.get(src!);
  expect(photoResponse.status()).toBe(200);
  expect(photoResponse.headers()['content-type']).toContain('image/png');
  await page.goto('/admin/inventory');
  await page.getByLabel('Nome, código ou final da etiqueta').fill(name);
  await page.getByRole('button', { name: 'Buscar', exact: true }).click();
  const stockCard = page.locator('.stock-card').filter({hasText: name});
  await expect(stockCard).toContainText('8');
  await expect(stockCard.locator('app-product-photo img')).toBeVisible();
});

test('cadastro de produto sem campos nem foto gera material provisório', async ({ page }) => {
  await login(page);
  await page.goto('/admin/products');
  await page.getByRole('button', { name: /Novo produto/ }).click();
  const form = page.getByRole('dialog', { name: 'Novo produto' });
  await expect(form.getByLabel('Nome do produto (opcional)')).toHaveCount(1);
  await form.getByRole('button', { name: 'Cadastrar produto' }).click();
  await expect(page.locator('.success-notice')).toContainText('Material sem nome');
  await expect(page.locator('.product-form')).toHaveCount(0);
});


test('busca rápida navega entre áreas com Ctrl+K', async ({ page }) => {
  await login(page);
  await page.keyboard.press('Control+k');
  const dialog = page.getByRole('dialog', { name: 'Busca rápida' });
  await expect(dialog).toBeVisible();
  await dialog.getByRole('searchbox', { name: 'Buscar área ou tarefa' }).fill('fornecedor');
  await dialog.getByRole('button', { name: /Fornecedores/ }).click();
  await expect(page).toHaveURL(/\/admin\/suppliers/);
  await expect(dialog).toHaveCount(0);
});

test('assistente consulta dados e prepara importação em lote com prévia', async ({ page }) => {
  await login(page);
  await page.goto('/admin/assistant');
  await expect(page).toHaveTitle('Assistente | Loja');
  await page.getByRole('button', { name: 'Quantos produtos tenho?' }).click();
  await expect(page.locator('.assistant-answer')).toContainText('produtos ativos');
  await page.getByRole('button', { name: 'Adicionar muitos produtos' }).click();
  const name = 'Material E2E ' + randomUUID().slice(0, 8);
  await page.getByLabel('Lista de materiais').fill(name + ';9,90;4,00;12');
  await page.getByRole('button', { name: 'Revisar importação' }).click();
  await expect(page.locator('.import-row input[aria-label^="Nome"]')).toHaveValue(name);
  page.once('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: 'Confirmar cadastro de 1 produtos' }).click();
  await expect(page.getByRole('status')).toContainText('1 produtos cadastrados');
  await page.goto('/admin/products');
  await page.getByRole('searchbox', { name: 'Buscar produto' }).fill(name);
  await expect(page.locator('tr').filter({ hasText: name })).toBeVisible();
  await page.goto('/admin/inventory');
  await page.getByLabel('Nome, código ou final da etiqueta').fill(name);
  await page.getByRole('button', { name: 'Buscar', exact: true }).click();
  await expect(page.locator('.stock-card').filter({ hasText: name })).toContainText('12');
});

test('financeiro e relatórios oferecem filtros simples', async ({ page }) => {
  await login(page);
  await page.goto('/admin/finance');
  await expect(page.getByRole('button', { name: 'A receber', exact: true })).toBeVisible();
  await expect(page.getByText('Vencido a receber')).toBeVisible();
  await expect(page.getByText('Vencido a pagar')).toBeVisible();
  await page.getByRole('button', { name: 'A receber', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'A receber' })).toBeVisible();
  await page.goto('/admin/reports');
  await page.getByRole('button', { name: '7 dias' }).click();
  await expect(page.getByRole('heading', { name: 'Vendas por dia' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Quanto sobra das vendas de mercadorias?' })).toBeVisible();
  await expect(page.getByText('Lucro bruto estimado', { exact: true })).toBeVisible();
  await expect(page.getByText('Valor do estoque físico a custo')).toBeVisible();
  await expect(page.getByText('Materiais parados há 90 dias')).toBeVisible();
});

test('estoque alterna entre quadradinhos e lista', async ({ page }) => {
  await login(page);
  await page.goto('/admin/inventory');
  const cards = page.getByRole('button', { name: '▦ Quadradinhos' });
  const list = page.getByRole('button', { name: '☷ Lista' });
  await expect(cards).toHaveAttribute('aria-pressed','true');
  await list.click();
  await expect(list).toHaveAttribute('aria-pressed','true');
  await cards.click();
  await expect(cards).toHaveAttribute('aria-pressed','true');
});

test('menu lateral abre, recolhe e mantém acesso à navegação', async ({ page }) => {
  await login(page);
  const menu = page.getByRole('button', { name: 'Abrir ou recolher menu' });
  await expect(menu).toBeVisible();
  const mobile = (page.viewportSize()?.width ?? 1200) <= 760;
  if (mobile) {
    await expect(page.locator('.side-nav')).toBeHidden();
    await menu.click();
    await expect(page.locator('.side-nav')).toBeVisible();
    await page.locator('.side-nav').getByRole('link', { name: 'Estoque' }).click();
    await expect(page).toHaveURL(/\/admin\/inventory/);
    await expect(page.locator('.side-nav')).toBeHidden();
  } else {
    await menu.click();
    await expect(page.locator('.admin-shell')).toHaveClass(/menu-collapsed/);
    await expect(page.locator('.side-nav').getByRole('link', { name: 'Estoque' })).toBeAttached();
    await menu.click();
    await expect(page.locator('.admin-shell')).not.toHaveClass(/menu-collapsed/);
  }
});

test('financeiro apresenta movimentações de dinheiro em aba própria sem outro item lateral', async ({ page }) => {
  await login(page);
  await page.goto('/admin/finance');
  await expect(page.getByRole('button', { name: 'Contas e pagamentos' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Dinheiro físico' })).toBeVisible();
  await page.getByRole('button', { name: 'Dinheiro físico' }).click();
  await expect(page.getByRole('heading', { name: 'Dinheiro movimentado' })).toBeVisible();
  await expect(page.locator('.finance-section-switch').getByRole('button', { name: 'Dinheiro físico' }))
    .toHaveAttribute('aria-pressed', 'true');
  await page.getByRole('button', { name: 'Contas e pagamentos' }).click();
  await expect(page.getByRole('button', { name: 'A receber', exact: true })).toBeVisible();
});

test('dono consegue abrir cada área principal sem página vazia', async ({page}) => {
  await login(page);
  const routes = [
    ['home','Painel'], ['products','Produtos'], ['inventory','Estoque'],
    ['suppliers','Fornecedores'], ['customers','Clientes'],
    ['sales','Vendas e pedidos'], ['purchases','Compras'],
    ['finance','Financeiro'], ['reports','Relatórios'],
    ['users','Usuários'], ['notifications','Alertas por e-mail'],
    ['settings','Loja e auditoria'], ['assistant','Assistente'],
  ];
  for (const [route,title] of routes) {
    await page.goto('/admin/' + route);
    await expect(page).toHaveTitle(title + ' | Loja');
    await expect(page.locator('.workspace').getByRole('heading').first()).toBeVisible();
    await expect(page.locator('.workspace').getByRole('heading').first()).not.toBeEmpty();
  }
});
