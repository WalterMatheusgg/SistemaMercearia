import { Component, DestroyRef, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, catchError, debounceTime, distinctUntilChanged, of, switchMap, finalize } from 'rxjs';
import { BarcodeApi, BarcodeItem } from './barcode-api.service';

/** Um único campo para scanner HID, nome ou final do código (sem baixa automática). */
@Component({
  selector: 'app-barcode-search',
  standalone: true,
  template: `
    <div class="barcode-search">
      <label [for]="inputId">{{ label }}</label>
      <div class="search-control">
        <input [id]="inputId" [value]="query()" type="search" autocomplete="off"
          spellcheck="false" maxlength="80" aria-autocomplete="list"
          [attr.aria-controls]="inputId + '-suggestions'"
          [attr.aria-expanded]="suggestions().length > 0"
          [attr.aria-activedescendant]="active() >= 0 ? inputId + '-suggestion-' + active() : null"
          placeholder="Nome, código ou últimos 3 dígitos (ex.: 001)"
          (input)="onInput($event)" (keydown)="onKeydown($event)" />
        <button type="button" (click)="identify()" [disabled]="busy() || !query().trim()">
          {{ busy() ? 'Lendo...' : 'Identificar' }}
        </button>
      </div>
      <small>Bipe com o leitor e pressione Enter, ou digite para ver sugestões.</small>
      @if (searching()) { <p class="hint" role="status">Pesquisando materiais...</p> }
      @if (message()) { <p class="error" role="alert">{{ message() }}</p> }
      @if (suggestions().length > 0) {
        <div class="suggestions" [id]="inputId + '-suggestions'" role="listbox"
          aria-label="Materiais encontrados">
          @for (item of suggestions(); track item.productId; let i = $index) {
            <button type="button" role="option"
              [id]="inputId + '-suggestion-' + i"
              [class.active]="active() === i" [attr.aria-selected]="active() === i"
              (click)="choose(item)">
              <span class="product">
                <strong>{{ item.name }}</strong>
                <span class="meta">{{ item.barcode }} · {{ item.unit }} · Saldo: {{ item.available }}</span>
              </span>
              <span class="price">{{ formatPrice(item.price) }}</span>
            </button>
          }
        </div>
      } @else if (query().trim() && !searching() && noMatches()) {
        <p class="hint" role="status">Nenhuma etiqueta encontrada para "{{ query() }}".</p>
      }
    </div>
  `,
  styles: [`
    :host{display:block;min-width:0;width:100%}
    .barcode-search{position:relative;display:grid;gap:7px;width:100%;color:#234d3b;font-family:Inter,system-ui,sans-serif}
    label{font-size:12px;font-weight:800}
    .search-control{display:flex;gap:8px;min-width:0}
    input{width:100%;min-width:0;flex:1;min-height:46px;border:1px solid #b8d6c6;
      border-radius:9px;padding:11px;font:14px Inter,system-ui,sans-serif;color:#213d33;background:#fff}
    input:focus{outline:2px solid #83c4a3;outline-offset:1px}
    .search-control button{flex:0 0 auto;min-height:46px;padding:8px 14px;border:0;
      border-radius:9px;background:#1f684f;color:#fff;font-weight:800;cursor:pointer}
    button:disabled{opacity:.55;cursor:wait}
    small,.hint{font-size:11px;color:#5f7b6a;margin:0}
    .error{font-size:12px;background:#fcefeb;color:#a33932;padding:9px;border-radius:8px;margin:0}
    .suggestions{display:grid;border:1px solid #b7d8c5;background:#fff;border-radius:12px;
      overflow:hidden;box-shadow:0 13px 34px #133d2823;max-height:340px;overflow-y:auto}
    .suggestions button{display:flex;gap:12px;align-items:center;justify-content:space-between;text-align:left;
      width:100%;padding:12px 14px;border:0;border-bottom:1px solid #e8eee9;background:#fff;color:#25483b;cursor:pointer}
    .suggestions button:last-child{border-bottom:0}
    .suggestions button:hover,.suggestions button.active{background:#e8f7ed}
    .product{display:grid;gap:4px;min-width:0}
    .product strong{font-size:13px;line-height:1.4}
    .meta{color:#628477;font-size:11px;font-variant-numeric:tabular-nums;overflow-wrap:anywhere}
    .price{font-size:13px;font-weight:850;white-space:nowrap;color:#165c42}
    @media(max-width:560px){.search-control{flex-wrap:wrap}.search-control button{width:100%}}
  `],
})
export class BarcodeSearchComponent implements OnChanges {
  @Input() inputId = 'barcode-search';
  @Input() label = 'Buscar etiqueta';
  @Input() locationId = '';
  @Output() readonly picked = new EventEmitter<BarcodeItem>();

  private readonly api = inject(BarcodeApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly typed = new Subject<string>();
  readonly query = signal('');
  readonly suggestions = signal<BarcodeItem[]>([]);
  readonly active = signal(-1);
  readonly searching = signal(false);
  readonly busy = signal(false);
  readonly message = signal('');
  readonly noMatches = signal(false);

  constructor() {
    this.typed.pipe(
      debounceTime(180),
      distinctUntilChanged(),
      switchMap(term => {
        const value = term.trim();
        if (!value) return of([] as BarcodeItem[]);
        this.searching.set(true);
        return this.api.search(value, this.locationId).pipe(
          catchError(() => {
            this.message.set('Não foi possível consultar as etiquetas.');
            return of([] as BarcodeItem[]);
          }),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(items => {
      this.suggestions.set(items);
      this.searching.set(false);
      this.noMatches.set(Boolean(this.query().trim()) && items.length === 0 && !this.message());
      this.active.set(-1);
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['locationId'] && !changes['locationId'].firstChange) {
      this.reset();
    }
  }

  onInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.query.set(value);
    this.suggestions.set([]);
    this.active.set(-1);
    this.noMatches.set(false);
    this.message.set('');
    this.typed.next(value);
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'ArrowDown' && this.suggestions().length) {
      event.preventDefault();
      this.active.update(i => Math.min(i + 1, this.suggestions().length - 1));
    } else if (event.key === 'ArrowUp' && this.suggestions().length) {
      event.preventDefault();
      this.active.update(i => Math.max(i - 1, 0));
    } else if (event.key === 'Escape') {
      this.suggestions.set([]);
      this.active.set(-1);
    } else if (event.key === 'Enter') {
      event.preventDefault();
      this.identify();
    }
  }

  identify(): void {
    const value = this.query().trim();
    if (!value || this.busy()) return;
    if (/^LF[0-9]{10}$/i.test(value)) {
      this.message.set('');
      this.busy.set(true);
      this.typed.next('');
      this.suggestions.set([]);
      this.api.lookup(value.toUpperCase(), this.locationId)
        .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.busy.set(false)))
        .subscribe({
          next: item => this.choose(item),
          error: () => this.message.set('Código não encontrado. Confira a etiqueta ou pesquise pelo nome.'),
        });
      return;
    }
    const options = this.suggestions();
    if (options.length && this.active() >= 0) {
      this.choose(options[this.active()]);
    } else if (options.length === 1) {
      this.choose(options[0]);
    } else if (options.length > 1) {
      this.message.set('Há mais de um material. Selecione o correto na lista.');
    } else {
      // Enter após digitação rápida também dispara sugestões, sem seleção às cegas.
      this.typed.next(value);
    }
  }

  choose(item: BarcodeItem): void {
    this.reset();
    this.picked.emit(item);
  }

  formatPrice(value: string): string {
    return Number(value).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  private reset(): void {
    this.query.set('');
    this.suggestions.set([]);
    this.active.set(-1);
    this.searching.set(false);
    this.noMatches.set(false);
    this.message.set('');
    this.typed.next('');
  }
}
