import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { switchMap } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { CartService } from './cart.service';
import { StoreNavComponent } from './store-nav.component';
import { ActionStatusComponent } from '../commerce/action-status.component';
import { createAction, money, quantity, detail } from '../commerce/ui';
@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, StoreNavComponent, ActionStatusComponent],
  templateUrl: './cart.component.html',
  styleUrls: ['../commerce/ui.scss', './store.scss'],
})
export class CartComponent {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);
  readonly cart = inject(CartService);
  readonly action = createAction();
  readonly config = signal<any | null>(null);
  readonly preview = signal<any | null>(null);
  readonly error = signal('');
  readonly money = money;
  readonly quantity = quantity;
  fulfillment = 'PICKUP';
  address = '';
  paymentChoice = 'PAY_ON_PICKUP';
  note = '';
  calculating = false;
  private previewRevision = 0;

  /** A resposta de um cálculo antigo nunca deve confirmar um carrinho já alterado. */
  invalidatePreview(): void {
    this.previewRevision++;
    this.preview.set(null);
    this.error.set('');
  }

  onFulfillmentChange(): void {
    this.paymentChoice = this.fulfillment === 'DELIVERY' ? 'MANUAL_PIX' : 'PAY_ON_PICKUP';
    this.invalidatePreview();
  }

  constructor() {
    this.http
      .get<any>('/api/catalog/store')
      .subscribe({ next: (r) => this.config.set(r), error: (e) => this.error.set(detail(e)) });
  }
  change(id: string, value: string) {
    this.cart.update(id, value);
    this.invalidatePreview();
  }
  remove(id: string) {
    this.cart.remove(id);
    this.invalidatePreview();
  }
  body() {
    return {
      items: this.cart.items().map((i) => ({ productId: i.productId, quantity: i.quantity })),
      fulfillment: this.fulfillment,
      address: this.address,
      paymentChoice: this.paymentChoice,
      note: this.note,
      expectedTotal: this.preview()?.total ?? null,
    };
  }
  calculate() {
    if (this.calculating || this.action.locked()) return;
    const revision = ++this.previewRevision;
    const body = this.body();
    this.preview.set(null);
    this.error.set('');
    this.calculating = true;
    this.http
      .get('/api/auth/csrf')
      .pipe(switchMap(() => this.http.post<any>('/api/catalog/preview', body)))
      .subscribe({
        next: (result) => {
          if (revision === this.previewRevision) this.preview.set(result);
          this.calculating = false;
        },
        error: (cause) => {
          if (revision === this.previewRevision) this.error.set(detail(cause));
          this.calculating = false;
        },
      });
  }
  checkout() {
    if (this.calculating || this.action.locked() || !this.preview()) return;
    this.action.submit('/api/account/orders', this.body(), (r) => {
      this.cart.clear();
      void this.router.navigate(['/account'], { queryParams: { order: r.id } });
    });
  }
}
