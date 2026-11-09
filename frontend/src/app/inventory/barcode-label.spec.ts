import { describe, expect, it } from 'vitest';
import { code128Svg, shelfLabelHtml, shelfLabelsHtml } from './barcode-label';
import { BarcodeItem } from './barcode-api.service';

describe('Etiqueta Code 128 B', () => {
  it('gera barras vetoriais com tamanho e dígito verificador para código interno', () => {
    const svg = code128Svg('LF0000000001');
    expect(svg).toContain('Código de barras LF0000000001');
    expect(svg).toContain('<svg');
    expect(svg).toContain('<rect');
    expect(svg).toContain('height="58"');
    expect((svg.match(/<rect /g) ?? []).length).toBeGreaterThan(30);
  });

  it('mostra preço e código maior no formato único de etiqueta', () => {
    const product: BarcodeItem = {
      productId: 'p-1', barcode: 'LF0000000123', name: 'Bucha 6 mm',
      unit: 'UN', price: '2.50', physical: '40', available: '40', minimumQuantity: '5',
      quantityScale: 0, quantityStep: '1', revision: 1, active: true,
    };
    const shelf = shelfLabelHtml(product);
    expect(shelf).toContain('size:100mm 55mm');
    expect(shelf).toContain('FINAL 123');
    expect(shelf).toContain('2,50');
    expect(shelf).toContain('PREÇO UNITÁRIO');
    expect(shelf).toContain('DESCRIÇÃO DO PRODUTO');
    expect(shelf).toContain('preserveAspectRatio="none"');
    expect(shelf).toContain('height:19mm');
    expect(shelf).toContain('min-height:19mm');
    expect(shelf).toContain('LF0000000123');
    expect(shelf).toContain('<svg');
    expect(shelf).not.toContain('GAVETA');
    expect(shelf).not.toContain('PRATELEIRA');
  });

  it('reimprime etiquetas existentes sem mudar os códigos e com uma etiqueta por página', () => {
    const example: BarcodeItem = {
      productId: 'p-3', barcode: 'LF0000000123', name: 'Bucha 6 mm',
      unit: 'UN', price: '1.50', physical: '100', available: '100', minimumQuantity: '5',
      quantityScale: 0, quantityStep: '1', revision: 1, active: true,
    };
    const markup = shelfLabelsHtml([
      example, { ...example, productId: 'p-4', barcode: 'LF0000000456', name: 'Parafuso 8 mm' },
    ]);
    expect((markup.match(/<article class="label">/g) ?? []).length).toBe(2);
    expect(markup).toContain('LF0000000123');
    expect(markup).toContain('LF0000000456');
    expect(markup).toContain('PREÇO UNITÁRIO');
    expect((markup.match(/MATERIAIS E FERRAGENS/g) ?? []).length).toBe(2);
    expect(markup).toContain('page-break-after:always');
    expect(() => shelfLabelsHtml([])).toThrow();
  });

  it('escapa nome do produto, sem injetar HTML na etiqueta', () => {
    const product: BarcodeItem = {
      productId: 'p-2', barcode: 'LF0000000002', name: '<img src=x onerror=alert(1)>',
      unit: 'UN', price: '1', physical: '1', available: '1', minimumQuantity: '0',
      quantityScale: 0, quantityStep: '1', revision: 1, active: true,
    };
    const html = shelfLabelHtml(product);
    expect(html).not.toContain('<img src=x');
    expect(html).toContain('&lt;img');
  });

  it('não aceita valores arbitrários que poderiam entrar no HTML de impressão', () => {
    expect(() => code128Svg('<script>')).toThrow('inválido');
    expect(() => code128Svg('')).toThrow('inválido');
    expect(() => code128Svg('lf0000000001')).toThrow('inválido');
  });
});
