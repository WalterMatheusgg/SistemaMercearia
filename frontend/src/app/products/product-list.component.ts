import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, inject, signal, OnDestroy, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../auth/auth.service';
import { ProductPhotoComponent } from './product-photo.component';
import { RouterLink } from '@angular/router';
import { A11yModule } from '@angular/cdk/a11y';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
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
  imports: [ProductPhotoComponent, A11yModule, CurrencyPipe, RouterLink, FormsModule, ReactiveFormsModule, MatButtonModule, MatFormFieldModule,
    MatInputModule, MatPaginatorModule, MatProgressBarModule, MatSelectModule, MatTableModule],
  templateUrl: './product-list.component.html',
  styleUrl: './product-list.component.scss',
})
export class ProductListComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ProductsApi);
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  selectedPhoto: File | null = null;
  categoryName = '';
  unitName = '';
  unitScale = 0;
  unitStep = '1';
  readonly photoPreview = signal<string | null>(null);
  readonly managingReferences = signal(false);
  readonly addingCategory = signal(false);
  readonly addingUnit = signal(false);
  readonly referencesError = signal('');
  readonly referencesSuccess = signal('');
  readonly autoCategory = signal<Lookup | null>(null);
  private requestId = 0;
  private categoriesRequest = 0;
  private suggestionRequest = 0;

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
    initialQuantity: ['', Validators.pattern(/^(?:\d+(?:[.,]\d{1,4})?)?$/)],
    name: ['', Validators.maxLength(160)],
    description: ['', Validators.maxLength(2000)],
    categoryId: [''],
    baseUnitCode: [''],
    cost: ['', Validators.pattern(/^(?:\d+(?:[.,]\d{1,2})?)?$/)],
    price: ['', Validators.pattern(/^(?:\d+(?:[.,]\d{1,2})?)?$/)],
  });

  canManageReferences(): boolean {
    return this.auth.currentUser()?.roles.some(role => ['OWNER','MANAGER'].includes(role)) ?? false;
  }

  ngOnDestroy(): void { this.clearPhoto(); }

  private clearPhoto(): void {
    const previous = this.photoPreview();
    if (previous) URL.revokeObjectURL(previous);
    this.photoPreview.set(null);
    this.selectedPhoto = null;
  }

  selectPhoto(event: Event): void {
    const input = event.target as HTMLInputElement;
    const photo = input.files?.[0];
    if (!photo) return;
    if (!['image/jpeg','image/png','image/webp'].includes(photo.type) || photo.size > 3 * 1024 * 1024) {
      this.formError.set('Escolha uma imagem JPG, PNG ou WebP com até 3 MB.');
      input.value = '';
      return;
    }
    this.clearPhoto();
    this.selectedPhoto = photo;
    this.photoPreview.set(URL.createObjectURL(photo));
    this.formError.set('');
  }

  imageSearchUrl(): string {
    return 'https://www.google.com/search?tbm=isch&q=' +
      encodeURIComponent((this.form.controls.name.value || 'ferragens ferramentas') + ' produto');
  }

  /** A caixa inline só é limpa depois que o servidor realmente confirmar a criação. */
  createCategoryFromEditor(input: HTMLInputElement): void {
    this.createCategory(input.value, () => { input.value = ''; });
  }

  createCategory(rawName = this.categoryName, onSuccess?: () => void): void {
    const name = rawName.trim();
    this.referencesError.set('');
    this.referencesSuccess.set('');
    if (!this.canManageReferences()) {
      this.referencesError.set('Apenas o dono ou gerente pode criar categorias.');
      return;
    }
    if (!name) {
      this.referencesError.set('Informe o nome da nova categoria.');
      return;
    }
    if (this.addingCategory()) return;

    const existing = this.categories().find(item =>
      item.name.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('pt-BR') ===
      name.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('pt-BR'));
    if (existing) {
      this.form.controls.categoryId.setValue(existing.code);
      this.referencesSuccess.set('A categoria já existe e foi selecionada: ' + existing.name);
      this.categoryName = '';
      onSuccess?.();
      return;
    }

    this.addingCategory.set(true);
    this.http.post<{id:string;name:string}>('/api/admin/references/categories',
      { name, active: true, quantityScale: 0, quantityStep: '1' },
      { headers: { 'Idempotency-Key': crypto.randomUUID() } }).subscribe({
      next: value => {
        this.categories.update(items => [...items.filter(item => item.code !== value.id),
          {code:value.id,name:value.name}].sort((a,b)=>a.name.localeCompare(b.name)));
        this.form.controls.categoryId.setValue(value.id);
        this.categoryName='';
        onSuccess?.();
        this.addingCategory.set(false);
        this.formError.set('');
        this.referencesSuccess.set('Categoria criada e selecionada: ' + value.name);
        this.loadCategories();
      },
      error: e => {
        this.addingCategory.set(false);
        const detail = e.error?.detail ?? 'Não foi possível criar a categoria. Verifique as permissões ou se o nome já existe.';
        this.referencesError.set(detail);
        if (this.formOpen()) this.formError.set(detail);
      },
    });
  }

  private refreshAutoCategory(name: string): void {
    const request = ++this.suggestionRequest;
    if (!this.formOpen() || this.form.controls.categoryId.value) {
      this.autoCategory.set(null);
      return;
    }
    this.api.suggestCategory(name).subscribe({
      next: item => {
        if (request === this.suggestionRequest && !this.form.controls.categoryId.value)
          this.autoCategory.set(item);
      },
      error: () => {
        if (request === this.suggestionRequest) this.autoCategory.set(null);
      },
    });
  }

  categoryChanged(): void {
    this.refreshAutoCategory(this.form.controls.name.value);
  }

  createUnit(): void {
    const name = this.unitName.trim();
    if (!this.canManageReferences() || !name || this.addingUnit()) return;
    this.referencesError.set(''); this.addingUnit.set(true);
    const code = 'U' + crypto.randomUUID().slice(0,8).toUpperCase();
    this.http.post<{code:string;name:string}>('/api/admin/references/units',
      {code,name,active:true,quantityScale:this.unitScale,quantityStep:this.unitStep.replace(',','.')}).subscribe({
      next: item => {
        this.units.update(items => [...items,{code:item.code,name:item.name}]
          .sort((a,b)=>a.name.localeCompare(b.name)));
        this.form.controls.baseUnitCode.setValue(item.code);
        this.unitName=''; this.addingUnit.set(false);
      },
      error: e=> {this.addingUnit.set(false);
        this.referencesError.set(e.error?.detail ?? 'Confira nome, casas decimais e incremento da unidade.');},
    });
  }

  ngOnInit(): void {
    this.loadLookups();
    this.loadProducts();
    this.form.controls.name.valueChanges.pipe(debounceTime(250), distinctUntilChanged(),
      takeUntilDestroyed(this.destroyRef)).subscribe(name => this.refreshAutoCategory(name));
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
    this.clearPhoto();
    this.form.reset({
      initialQuantity: '',
      name: '',
      description: '',
      categoryId: '',
      baseUnitCode: '',
      cost: '',
      price: '',
    });
    this.formOpen.set(true);
    this.referencesError.set('');
    this.referencesSuccess.set('');
    this.refreshAutoCategory(this.form.controls.name.value);
  }

  startEdit(product: Product): void {
    this.editingId.set(product.id);
    this.clearPhoto();
    this.formError.set('');
    this.showDetails.set(!!product.description || product.cost !== '0.00');
    this.form.reset({
      initialQuantity: '',
      name: product.name,
      description: product.description ?? '',
      categoryId: product.categoryId,
      baseUnitCode: product.baseUnitCode,
      cost: product.cost,
      price: product.price,
    });
    this.formOpen.set(true);
    this.referencesError.set('');
    this.referencesSuccess.set('');
    this.autoCategory.set(null);
  }

  closeForm(): void {
    if (this.saving()) return;
    if (this.form.dirty && !window.confirm('Descartar as alterações não salvas?')) return;
    this.formOpen.set(false);
    this.clearPhoto();
    this.formError.set('');
    this.autoCategory.set(null);
  }

  save(): void {
    if (this.saving()) return;
    this.formError.set('');
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.formError.set('Revise os valores informados. Os campos podem ficar vazios.');
      return;
    }
    const raw = this.form.getRawValue();
    const input: ProductInput = {
      name: raw.name.trim(),
      description: raw.description.trim() || null,
      categoryId: raw.categoryId || null,
      baseUnitCode: raw.baseUnitCode,
      cost: raw.cost.trim() ? raw.cost.replace(',', '.') : '0.00',
      price: raw.price.trim() ? raw.price.replace(',', '.') : '0.00',
    };
    this.saving.set(true);
    const isEditing = !!this.editingId();
    const request = this.editingId()
      ? this.api.updateComplete(this.editingId()!, input, this.selectedPhoto ?? undefined)
      : this.api.createComplete(input,
          raw.initialQuantity.trim() ? raw.initialQuantity.replace(',', '.') : '0', this.selectedPhoto ?? undefined);
    request.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.clearPhoto();
        this.successMessage.set(isEditing
          ? 'Produto atualizado: ' + saved.name
          : 'Produto cadastrado: ' + saved.name +
            (Number(raw.initialQuantity.replace(',','.')) > 0 ? '. Saldo inicial registrado.' : '. Saldo inicial: zero.'));
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

  private loadCategories(): void {
    const request = ++this.categoriesRequest;
    this.api.categories().subscribe({
      next: items => {
        if (request === this.categoriesRequest) this.categories.set(items);
      },
      error: () => this.errorMessage.set('Não foi possível carregar as categorias.'),
    });
  }

  private loadLookups(): void {
    this.loadCategories();
    this.api.units().subscribe({
      next: (items) => this.units.set(items),
      error: () => this.errorMessage.set('Não foi possível carregar as unidades.'),
    });
  }
}
