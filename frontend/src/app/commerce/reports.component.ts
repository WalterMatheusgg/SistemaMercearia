import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { money, quantity, date, label, detail, today } from './ui';
@Component({
  standalone: true,
  imports: [FormsModule],
  templateUrl: './reports.component.html',
  styleUrl: './ui.scss',
})
export class ReportsComponent {
  private readonly http = inject(HttpClient);
  readonly report = signal<any | null>(null);
  readonly error = signal('');
  readonly money = money;
  readonly quantity = quantity;
  readonly date = date;
  readonly label = label;
  start = today().slice(0, 7) + '-01';
  end = today();
  loading = false;
  constructor() {
    this.load();
  }
  load() {
    this.loading = true;
    this.error.set('');
    this.http
      .get<any>('/api/admin/reports', { params: { start: this.start, end: this.end } })
      .subscribe({
        next: (r) => {
          this.report.set(r);
          this.loading = false;
        },
        error: (e) => {
          this.error.set(detail(e));
          this.loading = false;
        },
      });
  }
  export() {
    this.http
      .get('/api/admin/reports.csv', {
        params: { start: this.start, end: this.end },
        responseType: 'blob',
      })
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = 'relatorio-vendas.csv';
          anchor.click();
          URL.revokeObjectURL(url);
        },
        error: (e) => this.error.set(detail(e)),
      });
  }
}
