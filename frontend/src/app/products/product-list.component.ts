import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Lookup, Product, ProductInput, ProductsApi } from './products-api.service';

@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [CurrencyPipe, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatPaginatorModule, MatProgressBarModule, MatSelectModule, MatTableModule],
  templateUrl: './product-list.component.html',
  styleUrl: './product-list.component.scss',
})
export class ProductListComponent implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly api = inject(ProductsApi);

  readonly products = signal<Product[]>([]);
  readonly categories = signal<Lookup[]>([]);
  readonly units = signal<Lookup[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly errorMessage = signal('');
  readonly formError = signal('');
  readonly query = signal('');
  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);
  readonly totalElements = signal(0);
  readonly editingId = signal<string | null>(null);
  readonly formOpen = signal(false);
  readonly columns = ['code', 'product', 'category', 'price', 'status', 'actions'];
  readonly form = this.formBuilder.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(40)]],
    name: ['', [Validators.required, Validators.maxLength(160)]],
    description: ['', Validators.maxLength(2000)],
    categoryId: ['', Validators.required],
    baseUnitCode: ['', Validators.required],
    cost: ['', [Validators.required, Validators.pattern(/^\d+([.,]\d{1,2})?$/)]],
    price: ['', [Validators.required, Validators.pattern(/^\d+([.,]\d{1,2})?$/)]],
  });

  ngOnInit(): void {
    this.loadLookups();
    this.loadProducts();
  }

  loadProducts(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    this.api.list(this.pageIndex(), this.pageSize(), this.query()).subscribe({
      next: (result) => {
        this.products.set(result.content);
        this.totalElements.set(result.totalElements);
        this.loading.set(false);
      },
      error: (error: { error?: { detail?: string } }) => {
        this.errorMessage.set(error.error?.detail ?? 'Não foi possível carregar os produtos.');
        this.loading.set(false);
      },
    });
  }

  search(value: string): void {
    this.query.set(value);
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
    this.form.reset({ code: '', name: '', description: '', categoryId: '', baseUnitCode: '', cost: '', price: '' });
    this.formOpen.set(true);
  }

  startEdit(product: Product): void {
    this.editingId.set(product.id);
    this.formError.set('');
    this.form.reset({
      code: product.code,
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
    this.formOpen.set(false);
    this.formError.set('');
  }

  save(): void {
    this.formError.set('');
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const raw = this.form.getRawValue();
    const input: ProductInput = {
      ...raw,
      code: raw.code.trim(),
      name: raw.name.trim(),
      description: raw.description.trim() || null,
      cost: raw.cost.replace(',', '.'),
      price: raw.price.replace(',', '.'),
    };
    const request = this.editingId()
      ? this.api.update(this.editingId()!, input)
      : this.api.create(input);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.loadProducts();
      },
      error: (error: { error?: { detail?: string } }) => {
        this.saving.set(false);
        this.formError.set(error.error?.detail ?? 'Verifique os campos e tente novamente.');
      },
    });
  }

  deactivate(product: Product): void {
    if (!window.confirm(`Inativar o produto ${product.name}?`)) return;
    this.api.deactivate(product.id).subscribe({
      next: () => this.loadProducts(),
      error: (error: { error?: { detail?: string } }) =>
        this.errorMessage.set(error.error?.detail ?? 'Não foi possível inativar o produto.'),
    });
  }

  private loadLookups(): void {
    this.api.categories().subscribe({
      next: (items) => this.categories.set(items),
      error: () => this.errorMessage.set('Não foi possível carregar as categorias.'),
    });
    this.api.units().subscribe({
      next: (items) => this.units.set(items),
      error: () => this.errorMessage.set('Não foi possível carregar as unidades.'),
    });
  }
}