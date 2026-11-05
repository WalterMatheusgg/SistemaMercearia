import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

interface Summary {
  aiEnabled: boolean;
  activeProducts?: number;
  lowStock?: number;
  suppliers?: number;
  openReceivables?: string;
  openPayables?: string;
}
interface Material { name: string; price: string | number; cost: string | number; unit: string }
interface Preparation { items: Material[]; usedModel: boolean; note: string }
interface ImportResult { total: number; created: number; skipped: number }

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './assistant.component.html',
  styleUrl: './assistant.component.scss',
})
export class AssistantComponent {
  private readonly http = inject(HttpClient);
  readonly summary = signal<Summary | null>(null);
  readonly answer = signal('');
  readonly preview = signal<Material[]>([]);
  readonly busy = signal(false);
  readonly submitting = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly usedModel = signal(false);
  readonly section = signal<'ask' | 'import'>('ask');
  question = '';
  batch = '';
  useAi = false;
  questionAi = false;

  constructor() { this.refresh(); }
  refresh(): void {
    this.http.get<Summary>('/api/admin/assistant/summary').subscribe({
      next: value => this.summary.set(value),
      error: () => this.error.set('Não foi possível consultar as informações do assistente.'),
    });
  }
  ask(text?: string): void {
    if (text) this.question = text;
    if (!this.question.trim() || this.busy()) return;
    this.busy.set(true); this.error.set('');
    this.http.post<{ answer: string; usedModel: boolean }>('/api/admin/assistant/ask',
      { question: this.question.trim(), useAi: this.questionAi && !!this.summary()?.aiEnabled }).subscribe({
        next: value => {
          this.answer.set(value.answer); this.usedModel.set(value.usedModel); this.busy.set(false);
        },
        error: e => { this.error.set(e.error?.detail ?? 'Não foi possível responder.'); this.busy.set(false); },
      });
  }
  prepare(): void {
    if (!this.batch.trim() || this.busy()) return;
    this.busy.set(true); this.error.set(''); this.success.set('');
    this.preview.set([]);
    this.http.post<Preparation>('/api/admin/assistant/products/prepare',
      { text: this.batch, useAi: this.useAi && !!this.summary()?.aiEnabled }).subscribe({
        next: value => { this.preview.set(value.items); this.usedModel.set(value.usedModel); this.busy.set(false); },
        error: e => { this.error.set(e.error?.detail ?? 'Revise a lista de produtos.'); this.busy.set(false); },
      });
  }
  chooseFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (file.size > 35000 || !/\.(csv|txt)$/i.test(file.name)) {
      this.error.set('Selecione um CSV ou TXT com até 35 KB e 200 linhas.');
      input.value = ''; return;
    }
    file.text().then(value => {
      this.batch = value.replace(/^\uFEFF/, '');
      this.preview.set([]); this.error.set(''); this.success.set('');
      input.value = '';
    }).catch(() => this.error.set('Não foi possível ler o arquivo.'));
  }
  editItem(index: number, field: keyof Material, value: string): void {
    this.preview.update(items => items.map((item, i) => i === index ? { ...item, [field]: value } : item));
  }
  removeItem(index: number): void {
    this.preview.update(items => items.filter((_, i) => i !== index));
  }
  commit(): void {
    if (!this.preview().length || this.submitting()) return;
    if (!window.confirm('Cadastrar ' + this.preview().length
      + ' materiais revisados? Os já existentes serão ignorados. O estoque não será alterado.')) return;
    this.submitting.set(true); this.error.set('');
    this.http.post<ImportResult>('/api/admin/assistant/products/commit', { items: this.preview() }).subscribe({
      next: result => {
        this.success.set(result.created + ' produtos cadastrados; ' + result.skipped
          + ' já existentes ou repetidos ignorados.');
        this.preview.set([]); this.submitting.set(false); this.refresh();
      },
      error: e => { this.error.set(e.error?.detail ?? 'Não foi possível confirmar o lote.'); this.submitting.set(false); },
    });
  }
}
