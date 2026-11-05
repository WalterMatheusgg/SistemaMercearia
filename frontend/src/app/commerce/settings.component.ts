import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActionStatusComponent } from './action-status.component';
import { Row, Page, createAction, date, decimal, detail } from './ui';
@Component({
  standalone: true,
  imports: [FormsModule, ActionStatusComponent],
  templateUrl: './settings.component.html',
  styleUrl: './ui.scss',
})
export class SettingsComponent {
  private readonly http = inject(HttpClient);
  readonly action = createAction();
  readonly error = signal('');
  readonly loaded = signal(false);
  readonly audit = signal<Row[]>([]);
  readonly date = date;
  page = 0;
  total = 0;
  form = {
    name: '',
    phone: '',
    address: '',
    deliveryFee: '0.00',
    deliveryEnabled: false,
    paymentInstructions: '',
    reservationMinutes: 1440,
  };
  constructor() {
    this.load();
    this.loadAudit();
  }
  load() {
    this.http.get<any>('/api/admin/settings').subscribe({
      next: (r) => {
        this.form = {
          name: r.name,
          phone: r.phone,
          address: r.address,
          deliveryFee: r.deliveryFee,
          deliveryEnabled: r.deliveryEnabled,
          paymentInstructions: r.paymentInstructions,
          reservationMinutes: r.reservationMinutes,
        };
        this.loaded.set(true);
      },
      error: (e) => this.error.set(detail(e)),
    });
  }
  save() {
    this.action.submit(
      '/api/admin/settings',
      { ...this.form, deliveryFee: decimal(this.form.deliveryFee) },
      () => {
        this.load();
        this.loadAudit();
      },
      'PUT',
    );
  }
  loadAudit() {
    this.http.get<Page>('/api/admin/audit', { params: { page: this.page } }).subscribe({
      next: (r) => {
        this.audit.set(r.content);
        this.total = r.totalElements;
      },
      error: (e) => this.error.set(detail(e)),
    });
  }
}
