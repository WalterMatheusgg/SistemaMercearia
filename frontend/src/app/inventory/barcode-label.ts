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

/** Dois tamanhos de papel: etiqueta de prateleira ou gaveta/organizador. */
export type LabelSize = 'shelf' | 'drawer';

/** HTML autossuficiente e de alto contraste para impressoras comuns ou térmicas. */
export function shelfLabelHtml(item: BarcodeItem, size: LabelSize = 'shelf'): string {
  const svg = code128Svg(item.barcode);
  const drawer = size === 'drawer';
  const w = drawer ? 60 : 100;
  const h = drawer ? 40 : 55;
  const money = Number(item.price).toLocaleString('pt-BR', {
    minimumFractionDigits: 2, maximumFractionDigits: 2,
  });
  const nameClass = item.name.length > (drawer ? 27 : 50) ? 'name long' : 'name';
  return `<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
  <title>Etiqueta ${escapeHtml(item.barcode)} - ${escapeHtml(item.name)}</title>
  <style>
    @page { size: ${w}mm ${h}mm; margin: 0; }
    * { box-sizing: border-box; }
    html,body { margin:0; padding:0; font-family:Arial,Helvetica,sans-serif; color:#111; }
    .label {
      width:${w}mm; height:${h}mm; padding:${drawer ? '2' : '3'}mm;
      display:flex; flex-direction:column; overflow:hidden; background:#fff;
    }
    header { display:flex;align-items:center;justify-content:space-between;gap:2mm;
      padding-bottom:1.1mm;border-bottom:1.5px solid #111;white-space:nowrap; }
    .brand {font-weight:900;font-size:${drawer ? '10' : '13'}px;letter-spacing:.04em}
    .ref {font-weight:700;font-size:${drawer ? '8' : '10'}px;border:1px solid #111;padding:2px 4px}
    .name {font-size:${drawer ? '11' : '14'}px;font-weight:800;line-height:1.18;
      margin-top:${drawer ? '1.2' : '2'}mm;min-height:${drawer ? '8' : '10'}mm;
      max-height:${drawer ? '8.5' : '11'}mm;overflow:hidden;overflow-wrap:anywhere}
    .name.long {font-size:${drawer ? '9' : '11'}px}
    .details {display:flex;align-items:flex-end;justify-content:space-between;gap:2mm;
      margin:${drawer ? '0' : '1'}mm 0 1mm}
    .price {display:flex;align-items:baseline;gap:1mm;font-weight:900;line-height:1}
    .currency {font-size:${drawer ? '12' : '15'}px}
    .amount {font-size:${drawer ? '21' : '31'}px;letter-spacing:-.05em}
    .unit {font-size:${drawer ? '8' : '10'}px;text-align:right;font-weight:700}
    .barcode {width:100%;height:${drawer ? '9.5' : '11.5'}mm;overflow:hidden;margin-top:auto}
    .barcode svg {display:block;width:100%;height:100%}
    footer {display:flex;align-items:center;justify-content:space-between;gap:2mm;margin-top:1mm;
      font-family:Arial,monospace;font-size:${drawer ? '9' : '11'}px;letter-spacing:.08em;font-weight:700}
    footer small {font:normal ${drawer ? '7' : '9'}px Arial,sans-serif;letter-spacing:0}
    @media screen {
      body {min-height:100vh;display:grid;place-content:center;background:#e6e9e8}
      .label {border:1px solid #cdd5d1;box-shadow:0 12px 30px #0002}
    }
    @media print {html,body{width:${w}mm;height:${h}mm} .label{border:0;box-shadow:none}}
  </style></head><body><article class="label">
    <header><span class="brand">LOJA FERRAGENS</span>
      <span class="ref">FINAL ${escapeHtml(item.barcode.slice(-3))}</span></header>
    <div class="${nameClass}">${escapeHtml(item.name)}</div>
    <div class="details">
      <div class="price"><span class="currency">R$</span><span class="amount">${money}</span></div>
      <span class="unit">PREÇO POR ${escapeHtml(item.unit)}</span>
    </div>
    <div class="barcode">${svg}</div>
    <footer><span>${escapeHtml(item.barcode)}</span><small>CÓDIGO INTERNO · ${drawer ? 'GAVETA' : 'PRATELEIRA'}</small></footer>
  </article></body></html>`;
}

/** Reimpressão dos produtos já cadastrados, uma etiqueta por página de papel adesivo. */
export function shelfLabelsHtml(items: BarcodeItem[], size: LabelSize = 'shelf'): string {
  if (items.length === 0 || items.length > 20) {
    throw new Error('Prepare de 1 a 20 etiquetas por vez.');
  }
  const article = /<article class="label">[\\s\\S]*?<\\/article>/;
  const base = shelfLabelHtml(items[0], size);
  const labels = items.map(item => {
    const markup = shelfLabelHtml(item, size).match(article)?.[0];
    if (!markup) throw new Error('Não foi possível montar a etiqueta.');
    return markup;
  }).join('\\n');
  return base.replace(article, labels)
    .replace('</title>', ' · Reimpressão</title>')
    .replace('@media print {',
      '@media print {.label{break-after:page;page-break-after:always}.label:last-child{break-after:auto;page-break-after:auto}');
}

function openPrintPreview(html: string): void {
  const win = window.open('', '_blank', 'width=700,height=550');
  if (!win) throw new Error('O navegador bloqueou a impressão. Autorize a janela pop-up da Loja.');
  win.addEventListener('load', () => { win.focus(); win.print(); }, { once: true });
  win.document.open();
  win.document.write(html);
  win.document.close();
}

export function printShelfLabels(items: BarcodeItem[], size: LabelSize = 'shelf'): void {
  openPrintPreview(shelfLabelsHtml(items, size));
}

/** A janela de impressão não contém controles ou dados da interface principal. */
export function printShelfLabel(item: BarcodeItem, size: LabelSize = 'shelf'): void {
  openPrintPreview(shelfLabelHtml(item, size));
}
