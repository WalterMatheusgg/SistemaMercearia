import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { money, quantity, date, label, detail, today } from './ui';
@Component({
  standalone: true,
  imports: [FormsModule],
  templateUrl: './reports.component.html',
  styleUrls: ['./ui.scss','./reports.component.scss'],
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
  range: 'today' | 'week' | 'month' | 'custom' = 'month';
  private requestId = 0;

  chooseRange(range: 'today' | 'week' | 'month'): void {
    this.range = range;
    this.end = today();
    if (range === 'today') this.start = this.end;
    else if (range === 'month') this.start = this.end.slice(0,7) + '-01';
    else {
      const date = new Date(this.end + 'T12:00:00Z');
      date.setUTCDate(date.getUTCDate() - 6);
      this.start = date.toISOString().slice(0,10);
    }
    this.load();
  }

  barWidth(value: string | number, days: Array<{revenue: string}>): number {
    const total = Number(value);
    const max = Math.max(0, ...days.map(d => Number(d.revenue)));
    return !Number.isFinite(total) || max <= 0 ? 0 : Math.min(100,Math.max(0,total/max*100));
  }

  constructor() {
    this.load();
  }
  load() {
    const id = ++this.requestId;
    this.loading = true;
    this.error.set('');
    this.http
      .get<any>('/api/admin/reports', { params: { start: this.start, end: this.end } })
      .subscribe({
        next: (r) => {
          if (id !== this.requestId) return;
          this.report.set(r);
          this.loading = false;
        },
        error: (e) => {
          if (id !== this.requestId) return;
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
