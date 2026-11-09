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
    width="100%" height="100%"><g fill="#000">${rects.join('')}</g></svg>`;
}

const escapeHtml = (text: string) => text.replace(/[&<>"']/g,
  (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char] ?? char);

/** Modelo único de etiqueta, 100 × 55 mm, para novos e antigos produtos. */
export const LABEL_WIDTH_MM = 100;
export const LABEL_HEIGHT_MM = 55;

/** HTML autossuficiente e de alto contraste para impressoras comuns ou térmicas. */
export function shelfLabelHtml(item: BarcodeItem): string {
  const svg = code128Svg(item.barcode);
  const w = LABEL_WIDTH_MM;
  const h = LABEL_HEIGHT_MM;
  const money = Number(item.price).toLocaleString('pt-BR', {
    minimumFractionDigits: 2, maximumFractionDigits: 2,
  });
  if (!Number.isFinite(Number(item.price)) || Number(item.price) < 0) {
    throw new Error('Preço inválido para imprimir a etiqueta.');
  }
  const nameClass = item.name.length > 50 ? 'name long' : 'name';
  return `<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
  <title>Etiqueta ${escapeHtml(item.barcode)} - ${escapeHtml(item.name)}</title>
  <style>
    @page{size:100mm 55mm;margin:0}
    *{box-sizing:border-box}
    html,body{margin:0;padding:0;font-family:Arial,Helvetica,sans-serif;color:#101010}
    .label{width:100mm;height:55mm;padding:2.2mm 3.5mm;display:flex;
      flex-direction:column;overflow:hidden;background:#fff}
    header{display:flex;align-items:center;justify-content:space-between;gap:2mm;
      padding-bottom:.9mm;border-bottom:1px solid #222}
    .brand-wrap{display:grid;gap:.2mm;min-width:0}
    .brand{font-weight:900;font-size:12px;letter-spacing:.01em;white-space:nowrap}
    .brand-wrap small{font-size:6.5px;font-weight:700;letter-spacing:.04em}
    .ref{flex:0 0 auto;background:#111;color:#fff;font-weight:900;font-size:12px;
      padding:3px 6px;border-radius:2px}
    .name-label{display:block;margin-top:.8mm;color:#454545;font-size:7px;
      font-weight:700;letter-spacing:.06em}
    .name{font-size:13px;font-weight:900;line-height:1.12;margin-top:.2mm;
      min-height:6mm;max-height:7mm;overflow:hidden;overflow-wrap:anywhere}
    .name.long{font-size:11px}
    .details{display:flex;align-items:flex-end;justify-content:space-between;gap:2mm;
      margin:.4mm 0;padding-bottom:.4mm;border-bottom:1px solid #ddd}
    .price-block{display:grid;gap:.1mm;min-width:0}
    .price-title{font-size:7px;letter-spacing:.04em;font-weight:700}
    .price{display:flex;align-items:baseline;gap:1mm;font-weight:900;line-height:1}
    .currency{font-size:13px}
    .amount{font-size:25px;letter-spacing:-.03em}
    .unit{font-size:9px;text-align:right;font-weight:700}
    /* O código ocupa toda a largura disponível, sem cortar as zonas de silêncio. */
    .barcode{width:100%;height:19mm;min-height:19mm;overflow:hidden;margin-top:auto;background:#fff}
    .barcode svg{display:block;width:100%;height:100%}
    footer{display:flex;align-items:center;justify-content:space-between;gap:2mm;
      margin-top:.7mm;font-family:Arial,monospace;font-size:10px;font-weight:800;letter-spacing:.03em}
    footer small{font:normal 8px Arial,sans-serif;letter-spacing:0;color:#333}
    .code-text{white-space:nowrap}
    @media screen{body{min-height:100vh;display:grid;place-content:center;background:#e6e9e8}
      .label{border:1px solid #cdd5d1;box-shadow:0 12px 30px #0002}}
    @media print{html,body{width:100mm;height:55mm}.label{border:0;box-shadow:none}}
  </style></head><body><article class="label">
    <header>
      <div class="brand-wrap"><span class="brand">LOJA FERRAGENS</span><small>MATERIAIS E FERRAGENS</small></div>
      <span class="ref">FINAL ${escapeHtml(item.barcode.slice(-3))}</span>
    </header>
    <span class="name-label">DESCRIÇÃO DO PRODUTO</span>
    <div class="${nameClass}">${escapeHtml(item.name)}</div>
    <div class="details">
      <div class="price-block">
        <span class="price-title">PREÇO UNITÁRIO</span>
        <div class="price"><span class="currency">R$</span><span class="amount">${money}</span></div>
      </div>
      <span class="unit">POR ${escapeHtml(item.unit)}</span>
    </div>
    <div class="barcode">${svg}</div>
    <footer><span class="code-text">${escapeHtml(item.barcode)}</span><small>CÓDIGO INTERNO</small></footer>
  </article></body></html>`;
}

/** Reimpressão dos produtos já cadastrados, uma etiqueta por página de papel adesivo. */
export function shelfLabelsHtml(items: BarcodeItem[]): string {
  if (items.length === 0 || items.length > 20) {
    throw new Error('Prepare de 1 a 20 etiquetas por vez.');
  }
  const article = /<article class="label">[\s\S]*?<\/article>/;
  const base = shelfLabelHtml(items[0]);
  const labels = items.map(item => {
    const markup = shelfLabelHtml(item).match(article)?.[0];
    if (!markup) throw new Error('Não foi possível montar a etiqueta.');
    return markup;
  }).join('\n');
  return base.replace(article, labels)
    .replace('</title>', ' · Reimpressão</title>')
    .replace('@media print{',
      '@media print{.label{break-after:page;page-break-after:always}.label:last-child{break-after:auto;page-break-after:auto}');
}

function openPrintPreview(html: string): void {
  const win = window.open('', '_blank', 'width=700,height=550');
  if (!win) throw new Error('O navegador bloqueou a impressão. Autorize a janela pop-up da Loja.');
  win.addEventListener('load', () => { win.focus(); win.print(); }, { once: true });
  win.document.open();
  win.document.write(html);
  win.document.close();
}

export function printShelfLabels(items: BarcodeItem[]): void {
  openPrintPreview(shelfLabelsHtml(items));
}

/** A janela de impressão não contém controles ou dados da interface principal. */
export function printShelfLabel(item: BarcodeItem): void {
  openPrintPreview(shelfLabelHtml(item));
}
