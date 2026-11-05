import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Lookup, Product, ProductInput, ProductsApi } from './products-api.service';
import { portuguesePaginator } from './paginator-intl';

type StatusFilter = 'active' | 'inactive' | 'all';

@Component({
  selector: 'app-product-list',
  standalone: true,
  providers: [{ provide: MatPaginatorIntl, useFactory: portuguesePaginator }],
  imports: [CurrencyPipe, RouterLink, ReactiveFormsModule, MatButtonModule, MatFormFieldModule,
    MatInputModule, MatPaginatorModule, MatProgressBarModule, MatSelectModule, MatTableModule],
  templateUrl: './product-list.component.html',
  styleUrl: './product-list.component.scss',
})
export class ProductListComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ProductsApi);
  private requestId = 0;

  readonly products = signal<Product[]>([]);
  readonly categories = signal<Lookup[]>([]);
  readonly units = signal<Lookup[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly errorMessage = signal('');
  readonly formError = signal('');
  readonly successMessage = signal('');
  readonly query = signal('');
  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);
  readonly totalElements = signal(0);
  readonly editingId = signal<string | null>(null);
  readonly formOpen = signal(false);
  readonly showDetails = signal(false);
  readonly filter = signal<StatusFilter>('active');
  readonly columns = ['product', 'category', 'price', 'status', 'actions'];
  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(160)]],
    description: ['', Validators.maxLength(2000)],
    categoryId: ['', Validators.required],
    baseUnitCode: ['UN', Validators.required],
    cost: ['0', [Validators.required, Validators.pattern(/^\d+([.,]\d{1,2})?$/)]],
    price: ['0', [Validators.required, Validators.pattern(/^\d+([.,]\d{1,2})?$/)]],
  });

  ngOnInit(): void {
    this.loadLookups();
    this.loadProducts();
  }

  private selectedActive(): boolean | null {
    return this.filter() === 'all' ? null : this.filter() === 'active';
  }

  loadProducts(): void {
    const request = ++this.requestId;
    this.loading.set(true);
    this.errorMessage.set('');
    this.api.list(this.pageIndex(), this.pageSize(), this.query(), this.selectedActive()).subscribe({
      next: (result) => {
        if (request !== this.requestId) return;
        this.products.set(result.content);
        this.totalElements.set(result.totalElements);
        this.loading.set(false);
      },
      error: (error: { error?: { detail?: string } }) => {
        if (request !== this.requestId) return;
        this.errorMessage.set(error.error?.detail ?? 'Não foi possível consultar os produtos.');
        this.loading.set(false);
      },
    });
  }

  search(value: string): void {
    this.query.set(value.trim());
    this.pageIndex.set(0);
    this.loadProducts();
  }

  setFilter(filter: StatusFilter): void {
    this.filter.set(filter);
    this.pageIndex.set(0);
    this.loadProducts();
  }

  changePage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadProducts();
  }

  startCreate(): void {
    this.editingId.set(null);
    this.formError.set('');
    this.showDetails.set(false);
    this.form.reset({
      name: '',
      description: '',
      categoryId: this.categories()[0]?.code ?? '',
      baseUnitCode: this.units().find((unit) => unit.code === 'UN')?.code
        ?? this.units()[0]?.code ?? 'UN',
      cost: '0',
      price: '0',
    });
    this.formOpen.set(true);
  }

  startEdit(product: Product): void {
    this.editingId.set(product.id);
    this.formError.set('');
    this.showDetails.set(!!product.description || product.cost !== '0.00');
    this.form.reset({
      name: product.name,
      description: product.description ?? '',
      categoryId: product.categoryId,
      baseUnitCode: product.baseUnitCode,
      cost: product.cost,
      price: product.price,
    });
    this.formOpen.set(true);
  }

  closeForm(): void {
    if (this.saving()) return;
    if (this.form.dirty && !window.confirm('Descartar as alterações não salvas?')) return;
    this.formOpen.set(false);
    this.formError.set('');
  }

  save(): void {
    if (this.saving()) return;
    this.formError.set('');
    if (this.form.invalid || !this.form.controls.name.value.trim()) {
      this.form.markAllAsTouched();
      this.formError.set('Informe o nome do produto e revise os campos destacados.');
      return;
    }
    const raw = this.form.getRawValue();
    const input: ProductInput = {
      name: raw.name.trim(),
      description: raw.description.trim() || null,
      categoryId: raw.categoryId,
      baseUnitCode: raw.baseUnitCode,
      cost: raw.cost.replace(',', '.'),
      price: raw.price.replace(',', '.'),
    };
    this.saving.set(true);
    const isEditing = !!this.editingId();
    const request = this.editingId()
      ? this.api.update(this.editingId()!, input)
      : this.api.create(input);
    request.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.successMessage.set(isEditing
          ? 'Produto atualizado: ' + saved.name
          : 'Produto cadastrado: ' + saved.name + '. Para informar o saldo, abra Estoque.');
        this.loadProducts();
      },
      error: (error: { error?: { detail?: string } }) => {
        this.saving.set(false);
        this.formError.set(error.error?.detail ?? 'Não foi possível salvar. Confira os dados e tente novamente.');
      },
    });
  }

  deactivate(product: Product): void {
    if (!window.confirm('Inativar ' + product.name + '? O histórico continuará preservado.')) return;
    this.api.deactivate(product.id).subscribe({
      next: () => {
        this.successMessage.set('Produto inativado. Ele pode ser reativado no filtro Inativos.');
        this.loadProducts();
      },
      error: (error: { error?: { detail?: string } }) =>
        this.errorMessage.set(error.error?.detail ?? 'Não foi possível inativar o produto.'),
    });
  }

  reactivate(product: Product): void {
    this.api.reactivate(product.id).subscribe({
      next: () => {
        this.successMessage.set(product.name + ' está ativo novamente.');
        this.loadProducts();
      },
      error: (error: { error?: { detail?: string } }) =>
        this.errorMessage.set(error.error?.detail ?? 'Não foi possível reativar o produto.'),
    });
  }

  private loadLookups(): void {
    this.api.categories().subscribe({
      next: (items) => {
        this.categories.set(items);
        if (this.formOpen() && !this.editingId() && !this.form.controls.categoryId.value) {
          this.form.controls.categoryId.setValue(items[0]?.code ?? '');
        }
      },
      error: () => this.errorMessage.set('Não foi possível carregar as categorias.'),
    });
    this.api.units().subscribe({
      next: (items) => this.units.set(items),
      error: () => this.errorMessage.set('Não foi possível carregar as unidades.'),
    });
  }
}
