import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../auth/auth.service';
import { ActionStatusComponent } from './action-status.component';
import {
  Row,
  Page,
  createAction,
  money,
  quantity,
  date,
  label,
  decimal,
  detail,
  today,
} from './ui';
@Component({
  standalone: true,
  imports: [FormsModule, ActionStatusComponent],
  templateUrl: './sales.component.html',
  styleUrl: './ui.scss',
})
export class SalesComponent {
  private readonly http = inject(HttpClient);
  readonly auth = inject(AuthService);
  readonly action = createAction();
  readonly rows = signal<Row[]>([]);
  readonly selected = signal<Row | null>(null);
  readonly products = signal<Row[]>([]);
  readonly customers = signal<Row[]>([]);
  readonly preview = signal<any | null>(null);
  readonly error = signal('');
  readonly money = money;
  readonly quantity = quantity;
  readonly date = date;
  readonly label = label;
  readonly methods = ['CASH', 'PIX', 'DEBIT_CARD', 'CREDIT_CARD', 'TRANSFER'];
  page = 0;
  total = 0;
  status = '';
  open = false;
  productQuery = '';
  customerQuery = '';
  productId = '';
  amount = '1';
  discount = '0.00';
  customerId = '';
  note = '';
  quote = false;
  dueDate = today();
  lines: { productId: string; name: string; unit: string; quantity: string }[] = [];
  payments: { method: string; amount: string; note: string }[] = [];
  confirmPayments: { method: string; amount: string; note: string }[] = [];
  confirmDue = today();
  cancelReason = '';
  cashTendered = '';
  confirmTendered = '';
  readonly changeValue = signal<any | null>(null);
  constructor() {
    this.load();
    this.searchProducts();
    this.searchCustomers();
  }
  management() {
    return this.auth.currentUser()?.roles.some((r) => ['OWNER', 'MANAGER'].includes(r));
  }
  load() {
    this.http
      .get<Page>('/api/admin/sales', { params: { status: this.status, page: this.page } })
      .subscribe({
        next: (r) => {
          this.rows.set(r.content);
          this.total = r.totalElements;
        },
        error: (e) => this.error.set(detail(e)),
      });
  }
  searchProducts() {
    this.http.get<Page>('/api/admin/catalog', { params: { query: this.productQuery } }).subscribe({
      next: (r) => this.products.set(r.content),
      error: (e) => this.error.set(detail(e)),
    });
  }
  searchCustomers() {
    this.http
      .get<Page>('/api/admin/contacts/CUSTOMER', {
        params: { query: this.customerQuery, size: 100 },
      })
      .subscribe({
        next: (r) => this.customers.set(r.content.filter((c) => c['active'])),
        error: (e) => this.error.set(detail(e)),
      });
  }
  add() {
    const product = this.products().find((p) => p.id === this.productId);
    if (!product || !/^\d+([.,]\d{1,4})?$/.test(this.amount)) {
      this.error.set('Selecione um produto e quantidade válida.');
      return;
    }
    if (this.lines.some((l) => l.productId === product.id)) {
      this.error.set('O produto já está na lista. Ajuste a quantidade na linha existente.');
      return;
    }
    this.lines.push({
      productId: product.id,
      name: product['name'],
      unit: product['unit'],
      quantity: this.amount,
    });
    this.preview.set(null);
    this.error.set('');
  }
  body() {
    return {
      customerId: this.customerId || null,
      items: this.lines.map((l) => ({ productId: l.productId, quantity: decimal(l.quantity) })),
      discount: decimal(this.discount),
      note: this.note,
      quote: this.quote,
      dueDate: this.dueDate,
      payments: this.quote ? [] : this.payments.map((p) => ({ ...p, amount: decimal(p.amount) })),
      expectedTotal: this.preview()?.total ?? null,
      cashTendered: this.cashTendered ? decimal(this.cashTendered) : null,
    };
  }
  calculate() {
    this.http.post<any>('/api/admin/sales/preview', this.body()).subscribe({
      next: (r) => {
        this.preview.set(r);
        this.error.set('');
      },
      error: (e) => this.error.set(detail(e)),
    });
  }
  addPayment() {
    this.changeValue.set(null);
    this.payments.push({
      method: 'CASH',
      amount: this.preview()?.total ?? '',
      note: 'Pagamento conferido no balcão',
    });
  }
  hasCash() {
    return this.payments.some((p) => p.method === 'CASH');
  }
  calculateChange() {
    this.http
      .post<any>('/api/admin/sales/change', {
        payments: this.payments.map((p) => ({ ...p, amount: decimal(p.amount) })),
        tendered: this.cashTendered ? decimal(this.cashTendered) : null,
      })
      .subscribe({ next: (r) => this.changeValue.set(r), error: (e) => this.error.set(detail(e)) });
  }
  save() {
    if (!this.preview()) {
      this.error.set('Calcule e confira os valores antes de confirmar.');
      return;
    }
    this.action.submit('/api/admin/sales', this.body(), (r) => {
      this.open = false;
      this.lines = [];
      this.payments = [];
      this.cashTendered = '';
      this.changeValue.set(null);
      this.preview.set(null);
      this.show(r.id);
      this.load();
    });
  }
  show(id: string) {
    this.http.get<Row>('/api/admin/sales/' + id).subscribe({
      next: (r) => {
        this.selected.set(r);
        this.confirmPayments = [
          { method: 'PIX', amount: r['total'], note: 'Recebimento conferido pela loja' },
        ];
        this.cancelReason = '';
        this.confirmTendered = '';
        this.changeValue.set(null);
      },
      error: (e) => this.error.set(detail(e)),
    });
  }
  confirm() {
    const sale = this.selected();
    if (!sale) return;
    this.action.submit(
      '/api/admin/sales/' + sale.id + '/confirm',
      {
        payments: this.confirmPayments.map((p) => ({ ...p, amount: decimal(p.amount) })),
        cashTendered: this.confirmTendered ? decimal(this.confirmTendered) : null,
        dueDate: this.confirmDue,
      },
      (r) => {
        this.show(r.id);
        this.load();
      },
    );
  }
  cancel() {
    const sale = this.selected();
    if (
      !sale ||
      !window.confirm(
        'Confirmar cancelamento/devolução integral? Estoque e pagamentos serão compensados.',
      )
    )
      return;
    this.action.submit(
      '/api/admin/sales/' + sale.id + '/cancel',
      { reason: this.cancelReason },
      (r) => {
        this.show(r.id);
        this.load();
      },
    );
  }
  nextStatus(sale: Row): string {
    return (
      {
        PAID: 'READY',
        READY: sale['fulfillment'] === 'DELIVERY' ? 'DISPATCHED' : 'COMPLETED',
        DISPATCHED: 'COMPLETED',
      }[sale['status'] as 'PAID' | 'READY' | 'DISPATCHED'] ?? ''
    );
  }
  advance() {
    const sale = this.selected();
    if (sale)
      this.action.submit(
        '/api/admin/sales/' + sale.id + '/fulfillment',
        { status: this.nextStatus(sale) },
        (r) => {
          this.show(r.id);
          this.load();
        },
      );
  }
  print() {
    window.print();
  }
}
