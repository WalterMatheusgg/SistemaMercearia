import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { finalize } from 'rxjs';
import { money, quantity, date, label, detail, today } from './ui';

type Range = 'today' | 'days5' | 'days7' | 'month' | 'thisMonth' | 'custom';

export function beginningOfLastDays(end: string, days: number): string {
  const value = new Date(end + 'T12:00:00Z');
  value.setUTCDate(value.getUTCDate() - (days - 1));
  return value.toISOString().slice(0,10);
}

export function validReportRange(start: string, end: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(start) || !/^\d{4}-\d{2}-\d{2}$/.test(end) || start > end) return false;
  const s = new Date(start + 'T12:00:00Z'), e = new Date(end + 'T12:00:00Z');
  if (isNaN(s.getTime()) || isNaN(e.getTime())) return false;
  if (s.toISOString().slice(0,10) !== start || e.toISOString().slice(0,10) !== end) return false;
  return e.getTime() <= s.setUTCFullYear(s.getUTCFullYear() + 2);
}

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './reports.component.html',
  styleUrls: ['./ui.scss','./reports.component.scss'],
})
export class ReportsComponent {
  private readonly http = inject(HttpClient);
  readonly report = signal<any | null>(null);
  readonly error = signal('');
  readonly exportError = signal('');
  readonly exporting = signal(false);
  readonly money = money;
  readonly quantity = quantity;
  readonly date = date;
  readonly label = label;
  readonly todayLimit = today();
  start = today().slice(0,7) + '-01';
  end = today();
  appliedStart = this.start;
  appliedEnd = this.end;
  loading = false;
  range: Range = 'thisMonth';
  private requestId = 0;

  chooseRange(value: Exclude<Range,'custom'>): void {
    this.range = value;
    this.end = today();
    if (value === 'today') this.start = this.end;
    else if (value === 'thisMonth') this.start = this.end.slice(0,7) + '-01';
    else this.start = beginningOfLastDays(this.end, value === 'days5' ? 5 :
      value === 'days7' ? 7 : 30);
    this.load();
  }

  canApply(): boolean { return validReportRange(this.start, this.end); }
  hasUnappliedDates(): boolean {
    return this.start !== this.appliedStart || this.end !== this.appliedEnd;
  }

  percentage(value: string | number | null | undefined, signed = false): string {
    if (value == null || !Number.isFinite(Number(value))) return 'Sem comparação';
    const n = Number(value);
    return (signed && n > 0 ? '+' : '') +
      n.toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 }) + '%';
  }

  barWidth(value: string | number, days: Array<{revenue: string}>): number {
    const total = Number(value);
    const max = Math.max(0, ...days.map(d => Number(d.revenue)));
    return !Number.isFinite(total) || max <= 0 ? 0 : Math.min(100,Math.max(0,total/max*100));
  }

  constructor() { this.load(); }

  load(): void {
    if (!this.canApply()) {
      this.error.set('Escolha datas válidas, com início anterior ao fim e intervalo de até 2 anos.');
      return;
    }
    const start = this.start, end = this.end;
    const id = ++this.requestId;
    this.loading = true;
    this.error.set('');
    this.exportError.set('');
    this.http.get<any>('/api/admin/reports', { params: { start, end } })
      .subscribe({
        next: r => {
          if (id !== this.requestId) return;
          this.appliedStart = start;
          this.appliedEnd = end;
          this.report.set(r);
          this.loading = false;
        },
        error: e => {
          if (id !== this.requestId) return;
          this.error.set(detail(e));
          this.loading = false;
        },
      });
  }

  export(): void {
    if (this.exporting() || this.loading || this.hasUnappliedDates() || !this.report()) return;
    this.exportError.set('');
    this.exporting.set(true);
    this.http.get('/api/admin/reports.xlsx', {
      params: { start: this.appliedStart, end: this.appliedEnd },
      responseType: 'blob',
    }).pipe(finalize(() => this.exporting.set(false)))
      .subscribe({
        next: blob => {
          const url = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = `relatorio-loja-${this.appliedStart}_a_${this.appliedEnd}.xlsx`;
          document.body.appendChild(anchor);
          anchor.click();
          anchor.remove();
          setTimeout(() => URL.revokeObjectURL(url), 1000);
        },
        error: async error => {
          try {
            const response = error?.error instanceof Blob ? await error.error.text() : '';
            const body = JSON.parse(response);
            this.exportError.set(body.detail || body.title || 'Não foi possível gerar o Excel.');
          } catch {
            this.exportError.set('Não foi possível gerar a planilha. Confira a conexão ou escolha um período menor.');
          }
        },
      });
  }
}
