import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../auth/auth.service';
import { Row, createAction, money, date, label, decimal, detail } from './ui';
import { ActionStatusComponent } from './action-status.component';
@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, ActionStatusComponent],
  templateUrl: './cash.component.html',
  styleUrls: ['./ui.scss', './cash.component.scss'],
})
export class CashComponent {
  private readonly http = inject(HttpClient);
  readonly auth = inject(AuthService);
  readonly action = createAction();
  readonly state = signal<any>({ current: {}, sessions: [] });
  readonly selected = signal<Row | null>(null);
  readonly error = signal('');
  readonly money = money;
  readonly date = date;
  readonly label = label;
  opening = '0.00';
  adjustment = { amount: '', kind: 'WITHDRAWAL', reason: '' };
  closing = { counted: '', note: '' };
  constructor() {
    this.load();
  }
  canWrite() {
    return this.auth.currentUser()?.roles.some((r) => ['OWNER', 'MANAGER', 'CASHIER'].includes(r));
  }
  load() {
    this.http
      .get<any>('/api/admin/cash')
      .subscribe({ next: (r) => this.state.set(r), error: (e) => this.error.set(detail(e)) });
  }
  open() {
    this.action.submit('/api/admin/cash/open', { amount: decimal(this.opening) }, () =>
      this.load(),
    );
  }
  adjust() {
    this.action.submit(
      '/api/admin/cash/adjust',
      { ...this.adjustment, amount: decimal(this.adjustment.amount) },
      () => {
        this.adjustment.amount = '';
        this.adjustment.reason = '';
        this.load();
      },
    );
  }
  close() {
    if (!window.confirm('Conferiu o dinheiro físico? Confirmar fechamento do caixa?')) return;
    this.action.submit(
      '/api/admin/cash/' + this.state().current.id + '/close',
      { ...this.closing, counted: decimal(this.closing.counted) },
      () => {
        this.closing = { counted: '', note: '' };
        this.load();
      },
    );
  }
  show(id: string) {
    this.http
      .get<Row>('/api/admin/cash/' + id)
      .subscribe({ next: (r) => this.selected.set(r), error: (e) => this.error.set(detail(e)) });
  }
}
