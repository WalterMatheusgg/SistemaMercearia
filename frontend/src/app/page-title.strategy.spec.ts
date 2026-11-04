import { pageTitle } from './page-title.strategy';
describe('Identidade das abas', () => {
  it('substitui o título genérico do navegador', () => {
    expect(pageTitle()).toBe('ForjaHub | Gestão e loja');
    expect(pageTitle('Estoque')).toBe('Estoque | ForjaHub');
    expect(pageTitle('Vendas e pedidos')).toBe('Vendas e pedidos | ForjaHub');
  });
});
