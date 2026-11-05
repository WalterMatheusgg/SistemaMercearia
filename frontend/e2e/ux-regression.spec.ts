import { expect, test, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';

const admin = process.env['E2E_ADMIN_USER'] ?? 'admin';
const password = process.env['E2E_ADMIN_PASSWORD'] ?? 'local-e2e-password-123456';

async function signIn(page: Page) {
  await page.goto('/login');
  await page.getByLabel('Usuário', { exact: true }).fill(admin);
  await page.getByLabel('Senha', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page).toHaveURL(/\/admin\/home/, { timeout: 12000 });
}

test('categorias e unidades são gerenciadas em Produtos, não em Configurações', async ({ page }) => {
  await signIn(page);
  await page.goto('/admin/products');
  const panel = page.locator('.references-panel');
  await expect(panel.locator('summary')).toHaveText('Gerenciar categorias e unidades');
  await panel.locator('summary').click();

  const category = 'Ferragens E2E ' + randomUUID().slice(0, 8);
  await panel.getByLabel('Nome da categoria').fill(category);
  await panel.getByRole('button', { name: 'Criar categoria' }).click();
  await expect(panel.locator('.reference-tags').first()).toContainText(category);

  const unit = 'Pacote E2E ' + randomUUID().slice(0, 8);
  await panel.getByLabel('Nome da unidade').fill(unit);
  await panel.getByRole('button', { name: 'Criar unidade' }).click();
  await expect(panel.locator('.reference-tags').nth(1)).toContainText(unit);

  await page.goto('/admin/settings');
  await expect(page.locator('.references-panel')).toHaveCount(0);
});

test('categoria criada no cadastro aparece nas listas e filtra o estoque', async ({ page }) => {
  await signIn(page);
  await page.goto('/admin/products');
  await page.getByRole('button', { name: /Novo produto/ }).click();
  const form = page.getByRole('dialog', { name: 'Novo produto' });
  const material = 'Parafuso E2E ' + randomUUID().slice(0, 8);
  const category = 'Ferragens E2E ' + randomUUID().slice(0, 8);
  await form.getByLabel('Nome do produto (opcional)').fill(material);
  await expect(form).toContainText('Sugestão pelo nome: Fixação');

  const inline = form.locator('#inlineCategoryName');
  await inline.fill(category);
  await form.getByRole('button', { name: 'Criar', exact: true }).click();
  await expect(form.getByRole('status')).toContainText('Categoria criada e selecionada');
  await expect(form.getByRole('combobox', { name: 'Categoria (opcional)' })).toContainText(category);

  await form.getByRole('button', { name: 'Cadastrar produto' }).click();
  await expect(page.locator('.success-notice')).toContainText('Produto cadastrado');
  const panel = page.locator('.references-panel');
  await panel.locator('summary').click();
  await expect(panel.locator('.reference-tags').first()).toContainText(category);

  await page.goto('/admin/inventory');
  const filter = page.getByRole('combobox', { name: 'Categoria do estoque' });
  await filter.selectOption({ label: category });
  await expect(page.locator('.stock-card').filter({ hasText: material })).toBeVisible();
  await expect(page.locator('.stock-view-title')).toContainText('1 produto(s) neste filtro');
  await filter.selectOption('');
  await expect(page.locator('.stock-view-title')).toContainText('produto(s) neste filtro');
});

test('foto do produto: câmera, dispositivo, busca e validação do arquivo', async ({ page }) => {
  await signIn(page);
  await page.goto('/admin/products');
  await page.getByRole('button', { name: /Novo produto/ }).click();
  const form = page.getByRole('dialog', { name: 'Novo produto' });

  await expect(form.getByRole('button', { name: 'Tirar foto', exact: true })).toBeVisible();
  await expect(form.getByRole('button', { name: 'Procurar no dispositivo', exact: true })).toBeVisible();
  await expect(form.getByRole('link', { name: /Buscar no Google/ }))
    .toHaveAttribute('href', /google\.com\/search\?tbm=isch/);
  await expect(form.locator('input[type="file"][capture]'))
    .toHaveAttribute('capture', 'environment');

  const chooser = form.locator('input[type="file"][aria-label="Selecionar foto do dispositivo"]');
  await chooser.setInputFiles({
    name: 'arquivo-invalido.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('arquivo invalido'),
  });
  await expect(form.getByRole('alert')).toContainText('JPG, PNG ou WebP');

  const png = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4//8/AwAI/AL+XoSMAAAAAElFTkSuQmCC',
    'base64',
  );
  await chooser.setInputFiles({ name: 'produto.png', mimeType: 'image/png', buffer: png });
  await expect(form.getByRole('img', { name: 'Prévia da foto selecionada' })).toBeVisible();
  await expect(form.getByRole('alert')).toHaveCount(0);
});

test('importação com nome e valores vazios aceita quantidade informada', async ({ page }) => {
  await signIn(page);
  await page.goto('/admin/assistant');
  await page.getByRole('button', { name: 'Adicionar muitos produtos' }).click();
  await page.getByLabel('Lista de materiais').fill(';;;7');
  await page.getByRole('button', { name: 'Revisar importação' }).click();
  await expect(page.getByLabel('Nome 1')).toHaveValue('');
  await expect(page.getByLabel('Quantidade 1')).toHaveValue('7');
  page.once('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: 'Confirmar cadastro de 1 produtos' }).click();
  await expect(page.getByRole('status')).toContainText('1 produtos cadastrados');
});

test('marca, filtros e atalho Produtos para Entrada no Estoque', async ({ page }) => {
  await signIn(page);
  await page.goto('/admin/products');
  await page.getByRole('button', { name: /Novo produto/ }).click();
  const form = page.getByRole('dialog', { name: 'Novo produto' });
  const name = 'Parafuso E2E ' + randomUUID().slice(0, 8);
  const brand = 'Marca E2E ' + randomUUID().slice(0, 8);
  await form.getByLabel('Nome do produto (opcional)').fill(name);
  await form.getByLabel('Preço de referência (R$) — opcional').fill('14,90');
  await form.getByRole('button', { name: /Adicionar custo ou observações/ }).click();
  await form.getByLabel('Marca (opcional)').fill(brand);
  await form.getByRole('button', { name: 'Cadastrar produto' }).click();
  await expect(page.locator('.success-notice')).toContainText('Produto cadastrado');
  const brandFilter = page.getByRole('combobox', { name: 'Filtrar marca dos produtos' });
  await expect(brandFilter.locator('option', { hasText: brand })).toHaveCount(1);
  await brandFilter.selectOption(brand);
  const row = page.locator('tr').filter({ hasText: name });
  await expect(row).toContainText(brand);
  await row.getByRole('link', { name: '+ Entrada' }).click();
  await expect(page).toHaveURL(/\/admin\/inventory/);
  await expect(page.getByRole('dialog', { name: 'Registrar movimentação' })).toBeVisible();
});

test('reposição sugere quantidade e prepara compra sem criar pedido', async ({ page }) => {
  await signIn(page);
  await page.goto('/admin/products');
  await page.getByRole('button', { name: /Novo produto/ }).click();
  const form = page.getByRole('dialog', { name: 'Novo produto' });
  const name = 'Bucha E2E ' + randomUUID().slice(0, 8);
  await form.getByLabel('Nome do produto (opcional)').fill(name);
  await form.getByLabel('Quantidade inicial em estoque (opcional)').fill('3');
  await form.getByRole('button', { name: 'Cadastrar produto' }).click();
  await expect(page.locator('.success-notice')).toContainText('Saldo inicial registrado');

  await page.goto('/admin/inventory');
  await page.getByLabel('Nome do produto').fill(name);
  await page.getByRole('button', { name: 'Buscar', exact: true }).click();
  const card = page.locator('.stock-card').filter({ hasText: name });
  await expect(card).toBeVisible();
  await card.getByRole('button', { name: 'Definir estoque mínimo' }).click();
  const movement = page.getByRole('dialog', { name: 'Registrar movimentação' });
  await movement.getByLabel('Estoque mínimo (UN)').fill('5');
  await movement.getByLabel('Motivo').fill('Definir mínimo para reposição');
  await movement.getByRole('button', { name: 'Confirmar movimentação' }).click();
  await expect(page.getByRole('status')).toContainText('Alteração do mínimo');

  const replenishment = page.locator('.stock-replenishment');
  await replenishment.locator('summary').click();
  const suggestion = replenishment.locator('.replenishment-item').filter({ hasText: name });
  await expect(suggestion).toContainText('+7');
  await suggestion.getByRole('link', { name: 'Preparar pedido' }).click();
  await expect(page).toHaveURL(/\/admin\/purchases/);
  await expect(page.getByText(/Sugestão de reposição carregada/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Registrar pedido de compra' })).toBeDisabled();
});

test('rodapé, navegação por teclado e textos promocionais removidos', async ({ page }) => {
  await signIn(page);
  await expect(page.locator('.app-footer')).toHaveText('© 2026');
  await expect(page.getByText('Mais controle. Menos trabalho.')).toHaveCount(0);
  await expect(page.getByText('Trabalho simplificado')).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Pular para o conteúdo' })).toHaveAttribute('href', '#workspace');

  if ((page.viewportSize()?.width ?? 1200) <= 760) {
    const menu = page.getByRole('button', { name: 'Abrir ou recolher menu' });
    await menu.click();
    await expect(page.locator('.side-nav')).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.locator('.side-nav')).toBeHidden();
  }
});

test('páginas principais permanecem dentro da largura de tela', async ({ page }) => {
  await signIn(page);
  const routes = [
    'home', 'products', 'inventory', 'suppliers', 'customers',
    'sales', 'purchases', 'finance', 'reports', 'users',
    'notifications', 'settings', 'assistant',
  ];
  for (const route of routes) {
    await page.goto('/admin/' + route);
    await expect(page.locator('.workspace').getByRole('heading').first()).toBeVisible();
    const width = await page.evaluate(() => ({
      viewport: window.innerWidth,
      document: document.documentElement.scrollWidth,
    }));
    expect(width.document, 'Página ' + route + ' excedeu a largura do ' +
      (page.viewportSize()?.width ?? 0) + 'px').toBeLessThanOrEqual(width.viewport + 2);
  }
});
