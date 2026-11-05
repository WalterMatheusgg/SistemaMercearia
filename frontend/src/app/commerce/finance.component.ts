import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActionStatusComponent } from './action-status.component';
import { Row, Page, createAction, money, date, label, decimal, detail, today } from './ui';
@Component({
  standalone: true,
  imports: [FormsModule, ActionStatusComponent],
  templateUrl: './finance.component.html',
  styleUrls: ['./ui.scss','./finance.component.scss'],
})
export class FinanceComponent {
  private readonly http = inject(HttpClient);
  readonly action = createAction();
  readonly rows = signal<Row[]>([]);
  readonly filter = signal<'pending'|'receivable'|'payable'|'finished'|'all'>('pending');
  private requested = 0;
  filterLabel(): string {
    return ({ pending:'Pendências', receivable:'A receber', payable:'A pagar',
      finished:'Já pagas', all:'Todas as contas' } as const)[this.filter()];
  }
  switchFilter(value: 'pending'|'receivable'|'payable'|'finished'|'all'): void {
    this.filter.set(value);
    this.direction = value === 'receivable' ? 'RECEIVABLE' : value === 'payable' ? 'PAYABLE' : '';
    this.status = value === 'pending' || value === 'receivable' || value === 'payable' ? 'OPEN,PARTIAL'
      : value === 'finished' ? 'PAID' : '';
    this.page = 0;
    this.load();
  }
  readonly selected = signal<Row | null>(null);
  readonly error = signal('');
  readonly money = money;
  readonly date = date;
  readonly label = label;
  readonly methods = ['CASH', 'PIX', 'DEBIT_CARD', 'CREDIT_CARD', 'TRANSFER'];
  direction = '';
  status = 'OPEN,PARTIAL';
  page = 0;
  total = 0;
  open = false;
  form = { direction: 'PAYABLE', description: '', amount: '', dueDate: today() };
  payment = { method: 'PIX', amount: '', note: '' };
  reason = '';
  constructor() {
    this.load();
  }
  load() {
    const request = ++this.requested;
    this.error.set('');
    this.http
      .get<Page>('/api/admin/finance', {
        params: { direction: this.direction, status: this.status, page: this.page },
      })
      .subscribe({
        next: (r) => {
          if (request !== this.requested) return;
          this.rows.set(r.content);
          this.total = r.totalElements;
        },
        error: (e) => { if (request === this.requested) this.error.set(detail(e)); },
      });
  }
  create() {
    this.action.submit(
      '/api/admin/finance',
      { ...this.form, amount: decimal(this.form.amount) },
      (r) => {
        this.open = false;
        this.form.description = '';
        this.form.amount = '';
        this.show(r.id);
        this.load();
      },
    );
  }
  show(id: string) {
    this.http.get<Row>('/api/admin/finance/' + id).subscribe({
      next: (r) => {
        this.selected.set(r);
        this.payment.amount = '';
        this.payment.note = '';
        this.reason = '';
      },
      error: (e) => this.error.set(detail(e)),
    });
  }
  settle() {
    const account = this.selected();
    if (account)
      this.action.submit(
        '/api/admin/finance/' + account.id + '/settle',
        { ...this.payment, amount: decimal(this.payment.amount) },
        (r) => {
          this.show(r.id);
          this.load();
        },
      );
  }
  reverse(id: string) {
    if (
      window.confirm('Confirmar estorno deste pagamento? A conta voltará a ter o valor pendente.')
    )
      this.action.submit(
        '/api/admin/finance/payments/' + id + '/reverse',
        { reason: this.reason },
        (r) => {
          this.show(r.id);
          this.load();
        },
      );
  }
}
