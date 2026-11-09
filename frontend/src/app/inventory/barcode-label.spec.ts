import { describe, expect, it } from 'vitest';
import { code128Svg } from './barcode-label';

describe('Etiqueta Code 128 B', () => {
  it('gera barras vetoriais com tamanho e dígito verificador para código interno', () => {
    const svg = code128Svg('LF0000000001');
    expect(svg).toContain('Código de barras LF0000000001');
    expect(svg).toContain('<svg');
    expect(svg).toContain('<rect');
    expect(svg).toContain('height="58"');
    expect((svg.match(/<rect /g) ?? []).length).toBeGreaterThan(30);
  });

  it('não aceita valores arbitrários que poderiam entrar no HTML de impressão', () => {
    expect(() => code128Svg('<script>')).toThrow('inválido');
    expect(() => code128Svg('')).toThrow('inválido');
    expect(() => code128Svg('lf0000000001')).toThrow('inválido');
  });
});
