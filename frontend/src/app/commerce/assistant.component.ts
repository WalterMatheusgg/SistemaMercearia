import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../auth/auth.service';

interface Summary {
  aiEnabled: boolean;
  activeProducts?: number;
  lowStock?: number;
  suppliers?: number;
  openReceivables?: string;
  openPayables?: string;
}
interface Diagnostic { failures24h: number; recent: Array<{occurredAt:string;requestMethod:string;endpoint:string;httpStatus:number}>; note:string }
interface Material { name: string; price: string | number; cost: string | number; quantity: string | number; unit: string }
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
  private readonly auth = inject(AuthService);
  readonly suggestions = signal<string[]>([]);
  readonly diagnostic = signal<Diagnostic | null>(null);
  readonly diagnosticError = signal('');
  readonly diagnosticsOpen = signal(false);
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
  canDiagnose(): boolean {
    return this.auth.currentUser()?.roles.some(r => r === 'OWNER' || r === 'MANAGER') ?? false;
  }
  openDiagnostics(): void { this.diagnosticsOpen.update(value => !value); if (this.diagnosticsOpen()) this.loadDiagnostics(); }
  loadDiagnostics(): void {
    if (!this.canDiagnose()) return;
    this.diagnosticError.set('');
    this.http.get<Diagnostic>('/api/admin/assistant/diagnostics').subscribe({
      next: result => this.diagnostic.set(result),
      error: () => this.diagnosticError.set('Não foi possível consultar as falhas técnicas agora.'),
    });
  }
  fillDemo(): void {
    if (this.batch.trim() && !window.confirm('Substituir a lista atual pelos exemplos de ferragens?')) return;
    this.batch = [
      'Furadeira de impacto 650 W', 'Jogo de chaves de fenda com 6 peças',
      'Martelo de unha 27 mm', 'Trena de aço 5 metros', 'Alicate universal 8 polegadas',
      'Serrote profissional 20 polegadas', 'Nível de bolha 40 cm',
      'Disco de corte inox 115 mm', 'Broca de aço rápido 6 mm',
      'Caixa de parafusos sextavados 8 mm',
    ].map(name => 'TESTE — ' + name + ';0;0;0').join('\n');
    this.preview.set([]); this.error.set(''); this.success.set('Exemplos preparados. Revise e confirme apenas se quiser inseri-los na base.');
  }
  refresh(): void {
    this.http.get<{suggestions:string[]}>('/api/admin/assistant/insights').subscribe({
      next: v=>this.suggestions.set(v.suggestions), error:()=>this.suggestions.set([]),
    });
    if (this.canDiagnose()) this.loadDiagnostics();
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
  private optionalDecimal(value: string | number): string | null {
    const text = String(value ?? '').trim();
    return text ? text.replace(',', '.') : null;
  }
  commit(): void {
    if (!this.preview().length || this.submitting()) return;
    if (!window.confirm('Cadastrar ' + this.preview().length
      + ' materiais revisados? Itens repetidos serão ignorados. As quantidades informadas serão registradas apenas nos novos produtos.')) return;
    this.submitting.set(true); this.error.set('');
    const items = this.preview().map(item => ({
      name: item.name?.trim() ?? '',
      price: this.optionalDecimal(item.price),
      cost: this.optionalDecimal(item.cost),
      quantity: this.optionalDecimal(item.quantity),
      unit: item.unit || 'UN',
    }));
    this.http.post<ImportResult>('/api/admin/assistant/products/commit', { items }).subscribe({
      next: result => {
        this.success.set(result.created + ' produtos cadastrados com as quantidades iniciais informadas; ' + result.skipped
          + ' já existentes ou repetidos ignorados.');
        this.preview.set([]); this.submitting.set(false); this.refresh();
      },
      error: e => { this.error.set(e.error?.detail ?? 'Não foi possível confirmar o lote.'); this.submitting.set(false); },
    });
  }
}
