import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { finalize, Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ProductPhotoComponent } from '../products/product-photo.component';
import { A11yModule } from '@angular/cdk/a11y';
import {
  InventoryApi,
  InventoryBalance,
  InventoryLocation,
  InventoryMovement,
  MovementCommand,
  MovementKind,
} from './inventory-api.service';

@Component({
  selector: 'app-inventory',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    A11yModule,
    RouterLink,
    ProductPhotoComponent,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
  ],
  templateUrl: './inventory.component.html',
  styleUrl: './inventory.component.scss',
})
export class InventoryComponent implements OnInit {
  private readonly api = inject(InventoryApi);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly typedSearch = new Subject<string>();
  private balanceRequest = 0;
  private historyRequest = 0;
  private operationKey = '';
  private submitted: MovementCommand | null = null;
  readonly locations = signal<InventoryLocation[]>([]);
  readonly locationId = signal('');
  readonly rows = signal<InventoryBalance[]>([]);
  readonly movements = signal<InventoryMovement[]>([]);
  readonly page = signal(0);
  readonly total = signal(0);
  readonly historyPage = signal(0);
  readonly historyTotal = signal(0);
  readonly loading = signal(false);
  readonly loadingHistory = signal(false);
  readonly saving = signal(false);
  readonly uncertain = signal(false);
  readonly error = signal('');
  readonly formError = signal('');
  readonly message = signal('');
  readonly selected = signal<InventoryBalance | null>(null);
  readonly historyProduct = signal<InventoryBalance | null>(null);
  readonly reverse = signal<InventoryMovement | null>(null);
  readonly formOpen = signal(false);
  readonly viewMode = signal<'cards'|'list'>('cards');
  readonly onlyLow = signal(false);
  readonly includeInactive = signal(false);
  readonly query = signal('');
  readonly historyKind = signal<MovementKind | ''>('');
  readonly labels: Record<MovementKind, string> = {
    INITIAL: 'Saldo inicial',
    ENTRY: 'Entrada',
    EXIT: 'Saída manual',
    LOSS: 'Perda',
    COUNT: 'Ajuste por contagem',
    MINIMUM: 'Alteração do mínimo',
    REVERSAL: 'Estorno',
  };
  readonly kinds: MovementKind[] = ['INITIAL', 'ENTRY', 'EXIT', 'LOSS', 'COUNT', 'MINIMUM'];
  readonly allKinds: MovementKind[] = [...this.kinds, 'REVERSAL'];
  readonly form = this.fb.nonNullable.group({
    kind: ['ENTRY' as MovementKind, Validators.required],
    quantity: ['', [Validators.required, Validators.pattern(/^\d+([.,]\d{1,4})?$/)]],
    reason: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(500)]],
  });

  canWrite(): boolean {
    return (
      this.auth.currentUser()?.roles.some((r) => ['OWNER', 'MANAGER', 'STOCKKEEPER'].includes(r)) ??
      false
    );
  }

  canAdjust(): boolean {
    return this.auth.currentUser()?.roles.some((r) => ['OWNER', 'MANAGER'].includes(r)) ?? false;
  }

  ngOnInit(): void {
    this.typedSearch.pipe(debounceTime(240), distinctUntilChanged(),
      takeUntilDestroyed(this.destroyRef)).subscribe(value => this.search(value));
    this.query.set(this.route.snapshot.queryParamMap.get('produto')?.trim() ?? '');
    this.api
      .locations()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (locations) => {
          this.locations.set(locations);
          this.locationId.set(locations[0]?.id ?? '');
          if (this.locationId()) this.refresh();
          else this.error.set('Nenhum local de estoque está disponível para sua loja.');
        },
        error: (e) =>
          this.error.set(this.detail(e, 'Não foi possível carregar os locais de estoque.')),
      });
  }

  refresh(): void {
    this.loadBalances();
    this.loadHistory();
  }

  changeLocation(id: string): void {
    this.locationId.set(id);
    this.page.set(0);
    this.historyPage.set(0);
    this.historyProduct.set(null);
    this.formOpen.set(false);
    this.refresh();
  }

  searchTyping(query: string): void { this.typedSearch.next(query); }

  search(query: string): void {
    this.query.set(query.trim());
    this.page.set(0);
    this.loadBalances();
  }

  loadBalances(): void {
    if (!this.locationId()) return;
    const request = ++this.balanceRequest;
    this.loading.set(true);
    this.error.set('');
    this.api
      .balances(
        this.locationId(),
        this.query(),
        this.onlyLow(),
        this.includeInactive(),
        this.page(),
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          if (request !== this.balanceRequest) return;
          this.rows.set(result.content);
          this.total.set(result.totalElements);
          this.loading.set(false);
          const updated = result.content.find(
            (row) => row.productId === this.historyProduct()?.productId,
          );
          if (updated) this.historyProduct.set(updated);
        },
        error: (e) => {
          if (request === this.balanceRequest) {
            this.loading.set(false);
            this.error.set(this.detail(e, 'Não foi possível consultar o estoque.'));
          }
        },
      });
  }

  loadHistory(): void {
    if (!this.locationId()) return;
    const request = ++this.historyRequest;
    this.loadingHistory.set(true);
    this.api
      .history(
        this.locationId(),
        this.historyProduct()?.productId ?? null,
        this.historyKind(),
        this.historyPage(),
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          if (request === this.historyRequest) {
            this.movements.set(result.content);
            this.historyTotal.set(result.totalElements);
            this.loadingHistory.set(false);
          }
        },
        error: (e) => {
          if (request === this.historyRequest) {
            this.loadingHistory.set(false);
            this.error.set(this.detail(e, 'Não foi possível consultar o histórico.'));
          }
        },
      });
  }

  showHistory(row: InventoryBalance | null): void {
    this.historyProduct.set(row);
    this.historyPage.set(0);
    this.loadHistory();
  }

  open(
    row: InventoryBalance,
    kind: MovementKind = 'ENTRY',
    reverse: InventoryMovement | null = null,
  ): void {
    if (this.formOpen() && !this.close()) return;
    this.selected.set(row);
    this.reverse.set(reverse);
    this.operationKey = crypto.randomUUID();
    this.submitted = null;
    this.uncertain.set(false);
    this.formError.set('');
    this.message.set('');
    this.form.enable();
    this.form.reset({
      kind,
      quantity: reverse
        ? reverse.delta.replace('-', '')
        : kind === 'COUNT'
          ? row.physical
          : kind === 'MINIMUM'
            ? row.minimumQuantity
            : '',
      reason: '',
    });
    this.formOpen.set(true);
  }

  startReversal(movement: InventoryMovement): void {
    const row = this.rows().find((item) => item.productId === movement.productId);
    if (!row) {
      this.error.set('Pesquise o produto na lista de saldos para registrar este estorno.');
      return;
    }
    this.open(row, 'REVERSAL', movement);
  }

  close(): boolean {
    if (this.saving()) return false;
    if (
      this.uncertain() &&
      !window.confirm(
        'O resultado ainda não foi confirmado. Confira o histórico antes de registrar outra operação. Fechar?',
      )
    )
      return false;
    this.formOpen.set(false);
    this.form.enable();
    this.uncertain.set(false);
    this.submitted = null;
    return true;
  }

  save(): void {
    if (this.saving() || !this.selected()) return;
    this.formError.set('');
    if (!this.submitted) {
      if (this.form.invalid || this.form.controls.reason.value.trim().length < 3) {
        this.form.markAllAsTouched();
        this.formError.set('Confira a quantidade e informe um motivo com pelo menos 3 caracteres.');
        return;
      }
      const values = this.form.getRawValue();
      this.submitted = {
        productId: this.selected()!.productId,
        locationId: this.locationId(),
        kind: values.kind,
        quantity: values.quantity.replace(',', '.'),
        reason: values.reason.trim(),
        expectedRevision: values.kind === 'COUNT' ? this.selected()!.revision : null,
        reversesMovementId: this.reverse()?.id ?? null,
      };
    }
    this.saving.set(true);
    this.form.disable();
    this.api
      .execute(this.submitted, this.operationKey)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: (movement) => {
          this.message.set(
            `${this.labels[movement.kind]} registrada. Saldo físico: ${this.number(movement.balanceAfter)} ${movement.unit}.`,
          );
          this.formOpen.set(false);
          this.form.enable();
          this.uncertain.set(false);
          this.submitted = null;
          this.refresh();
        },
        error: (e: { status?: number; error?: { detail?: string } }) => {
          if (!e.status || e.status >= 500) {
            this.uncertain.set(true);
            this.formError.set(
              'Não foi possível confirmar o resultado. Tente novamente: a mesma operação será consultada sem movimentar duas vezes.',
            );
          } else {
            const detail = this.detail(e, 'Não foi possível registrar a operação.');
            this.formError.set(
              e.status === 409 && this.submitted?.kind === 'COUNT'
                ? `${detail} Feche o formulário, atualize o estoque e confira a contagem antes de tentar novamente.`
                : detail,
            );
            this.form.enable();
            this.uncertain.set(false);
            this.submitted = null;
          }
        },
      });
  }

  nextPage(delta: number): void {
    this.page.update((page) => Math.max(0, page + delta));
    this.loadBalances();
  }
  nextHistoryPage(delta: number): void {
    this.historyPage.update((page) => Math.max(0, page + delta));
    this.loadHistory();
  }
  number(value: string): string {
    const [whole, fraction] = value.split('.');
    return whole.replace(/\B(?=(\d{3})+(?!\d))/g, '.') + (fraction ? ',' + fraction : '');
  }
  date(value: string): string {
    return new Intl.DateTimeFormat('pt-BR', {
      timeZone: 'America/Sao_Paulo',
      dateStyle: 'short',
      timeStyle: 'short',
    }).format(new Date(value));
  }
  status(value: InventoryBalance['status']): string {
    return { EMPTY: 'Sem estoque', LOW: 'Repor', NORMAL: 'Normal' }[value];
  }
  canReverse(m: InventoryMovement): boolean {
    return (
      this.canAdjust() &&
      !m.reversed &&
      m.kind !== 'REVERSAL' &&
      m.kind !== 'MINIMUM' &&
      m.delta !== '0'
    );
  }
  private detail(error: { error?: { detail?: string } }, fallback: string): string {
    return error.error?.detail ?? fallback;
  }
}
