import { pageTitle } from './page-title.strategy';
describe('Títulos das abas da Loja', () => {
  it('usa apenas Loja na raiz, sem o nome técnico frontend', () => {
    expect(pageTitle()).toBe('Loja');
  });
  it('identifica cada módulo do aplicativo', () => {
    expect(pageTitle('Estoque')).toBe('Estoque | Loja');
    expect(pageTitle('Vendas e pedidos')).toBe('Vendas e pedidos | Loja');
    expect(pageTitle('Minha conta')).toBe('Minha conta | Loja');
  });
});
