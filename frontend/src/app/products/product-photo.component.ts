import { Component, Input, OnChanges, signal } from '@angular/core';

/** Usa imagens privadas autenticadas pelo mesmo domínio; ilustração neutra se não houver foto. */
@Component({
  selector: 'app-product-photo',
  standalone: true,
  template: `
    <span class="product-picture" [class.no-image]="failed()" [class.fit-contain]="fit === 'contain'" aria-hidden="true">
      @if (id && !failed()) {
        <img [src]="'/api/admin/products/' + id + '/photo'" [alt]="alt"
          loading="lazy" (error)="failed.set(true)" />
      }
      @if (!id || failed()) {
        <svg viewBox="0 0 64 64" fill="none" stroke="currentColor"
          stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M13 22 32 13l19 9v24l-19 9-19-9z"/>
          <path d="m13 22 19 9 19-9M32 31v24M23 18l19 9"/>
        </svg>
      }
    </span>
  `,
  styles: [`
    :host{display:block;flex-shrink:0}
    .product-picture{display:grid;place-items:center;width:100%;height:100%;
      min-width:42px;min-height:42px;overflow:hidden;border-radius:inherit;background:#edf4ef;color:#4c9476}
    img{width:100%;height:100%;object-fit:cover;display:block}
    .product-picture.fit-contain{background:#fff}
    .product-picture.fit-contain img{object-fit:contain;padding:8px}
    svg{width:44%;height:44%;max-width:52px;opacity:.85}
  `],
})
export class ProductPhotoComponent implements OnChanges {
  @Input() id: string | null | undefined;
  @Input() alt = 'Foto do produto';
  @Input() fit: 'cover' | 'contain' = 'cover';
  readonly failed = signal(false);
  ngOnChanges(): void { this.failed.set(false); }
}
