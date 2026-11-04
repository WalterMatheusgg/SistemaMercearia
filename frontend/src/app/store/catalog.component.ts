import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { CartService } from './cart.service';
import { StoreNavComponent } from './store-nav.component';
import { Row, Page, money, quantity, detail } from '../commerce/ui';
@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, StoreNavComponent],
  templateUrl: './catalog.component.html',
  styleUrls: ['../commerce/ui.scss', './store.scss'],
})
export class CatalogComponent {
  private readonly http = inject(HttpClient);
  readonly cart = inject(CartService);
  readonly products = signal<Row[]>([]);
  readonly store = signal<any | null>(null);
  readonly error = signal('');
  readonly added = signal('');
  readonly money = money;
  readonly quantity = quantity;
  query = '';
  page = 0;
  total = 0;
  loading = false;
  quantities: Record<string, string> = {};
  constructor() {
    this.load();
    this.http
      .get<any>('/api/catalog/store')
      .subscribe({ next: (r) => this.store.set(r), error: (e) => this.error.set(detail(e)) });
  }
  load() {
    this.loading = true;
    this.http
      .get<Page>('/api/catalog', { params: { query: this.query, page: this.page } })
      .subscribe({
        next: (r) => {
          this.products.set(r.content);
          this.total = r.totalElements;
          this.loading = false;
          for (const p of r.content) this.quantities[p.id] ??= String(p['quantityStep']);
        },
        error: (e) => {
          this.error.set(detail(e));
          this.loading = false;
        },
      });
  }
  add(product: Row) {
    const value = (this.quantities[product.id] ?? '1').replace(',', '.');
    if (!/^\d+([.]\d{1,4})?$/.test(value) || Number(value) <= 0) {
      this.error.set('Informe uma quantidade válida.');
      return;
    }
    this.cart.add({
      productId: product.id,
      name: product['name'],
      code: product['code'],
      unit: product['unit'],
      quantity: value,
      price: product['price'],
    });
    this.added.set(
      product['name'] + ' adicionado ao carrinho. A quantidade é confirmada ao finalizar o pedido.',
    );
    this.error.set('');
  }
}
