import { BarcodeItem } from './barcode-api.service';

/** Padrão Code 128 B: largura de barras/espaços em módulos; último padrão é STOP. */
const PATTERNS = `212222 222122 222221 121223 121322 131222 122213 122312 132212
221213 221312 231212 112232 122132 122231 113222 123122 123221
223211 221132 221231 213212 223112 312131 311222 321122 321221
312212 322112 322211 212123 212321 232121 111323 131123 131321
112313 132113 132311 211313 231113 231311 112133 112331 132131
113123 113321 133121 313121 211331 231131 213113 213311 213131
311123 311321 331121 312113 312311 332111 314111 221411 431111
111224 111422 121124 121421 141122 141221 112214 112412 122114
122411 142112 142211 241211 221114 413111 241112 134111 111242
121142 121241 114212 124112 124211 411212 421112 421211 212141
214121 412121 111143 111341 131141 114113 114311 411113 411311
113141 114131 311141 411131 211412 211214 211232 2331112`.trim().split(/\s+/);

/** Gera barras vetoriais com checksum Code 128 B e zonas de silêncio. */
export function code128Svg(value: string): string {
  if (!/^[A-Z0-9-]{1,32}$/.test(value)) {
    throw new Error('Código de barras inválido para impressão.');
  }
  const START_B = 104;
  const symbols = Array.from(value, (c) => c.charCodeAt(0) - 32);
  let sum = START_B;
  symbols.forEach((s, i) => { sum += s * (i + 1); });
  const sequence = [START_B, ...symbols, sum % 103, 106];
  let x = 11;
  const rects: string[] = [];
  for (const symbol of sequence) {
    const widths = PATTERNS[symbol];
    if (!widths) throw new Error('Padrão Code 128 indisponível.');
    for (let i = 0; i < widths.length; i++) {
      const width = Number(widths[i]);
      if (i % 2 === 0) rects.push(`<rect x="${x}" y="0" width="${width}" height="58"/>`);
      x += width;
    }
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Código de barras ${value}"
    viewBox="0 0 ${x + 11} 58" preserveAspectRatio="none"
    width="100%" height="70"><g fill="#000">${rects.join('')}</g></svg>`;
}

const escapeHtml = (text: string) => text.replace(/[&<>"']/g,
  (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char] ?? char);

/** Uma janela isolada imprime somente a etiqueta, sem os dados da interface. */
export function printShelfLabel(item: BarcodeItem): void {
  const svg = code128Svg(item.barcode);
  const win = window.open('', '_blank', 'width=600,height=450');
  if (!win) throw new Error('O navegador bloqueou a impressão. Autorize a janela pop-up da Loja.');
  win.addEventListener('load', () => { win.focus(); win.print(); }, { once: true });
  win.document.open();
  win.document.write(`<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
  <title>Etiqueta - ${escapeHtml(item.name)}</title>
  <style>
    @page { size: 100mm 55mm; margin: 3mm; }
    * { box-sizing: border-box; } body { margin: 0; font: 12px Arial, sans-serif; color: #111; }
    .label { width: 94mm; height: 48mm; padding: 3mm; border: 1px solid #ddd; text-align: center; overflow: hidden; }
    h1 { font-size: 11px; margin: 0 0 3mm; letter-spacing: 1px; }
    h2 { font-size: 16px; margin: 0 0 2mm; overflow-wrap: anywhere; }
    .barcode { margin: 2mm auto 0; width: 85mm; }
    .code { font: bold 14px monospace; letter-spacing: 2px; margin-top: 2mm; }
    .unit { font-size: 11px; margin-top: 2mm; }
    @media screen { body { display: grid; place-content: center; min-height: 100vh; background: #eee; } .label { background: #fff; } }
    @media print { .label { border: 0; } }
  </style></head><body><section class="label">
  <h1>LOJA FERRAGENS</h1><h2>${escapeHtml(item.name)}</h2>
  <div class="barcode">${svg}</div><div class="code">${escapeHtml(item.barcode)}</div>
  <div class="unit">Unidade: ${escapeHtml(item.unit)} · Etiqueta de prateleira</div>
  </section></body></html>`);
  win.document.close();
}
