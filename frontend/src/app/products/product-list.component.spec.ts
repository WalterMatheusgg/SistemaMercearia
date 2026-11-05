import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { appConfig } from '../app.config';
import { ProductListComponent } from './product-list.component';
import { Product, ProductsApi } from './products-api.service';
import { AuthService } from '../auth/auth.service';

const product: Product = {
  id: 'product-1', code: 'FUR-001', name: 'Furadeira', description: null,
  categoryId: 'category-1', categoryName: 'Ferramentas', baseUnitCode: 'UN',
  baseUnitName: 'Unidade', cost: '120.00', price: '199.90', active: true,
  createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
};

describe('Cadastro simples de produtos', () => {
  const api = {
    list: vi.fn(), categories: vi.fn(), units: vi.fn(), brands: vi.fn(), get: vi.fn(),
    create: vi.fn(), update: vi.fn(), createComplete: vi.fn(), updateComplete: vi.fn(), deactivate: vi.fn(), reactivate: vi.fn(), suggestCategory: vi.fn(),
  };
  beforeEach(async () => {
    vi.resetAllMocks();
    api.list.mockReturnValue(of({ content: [product], page: 0, size: 20, totalElements: 1, totalPages: 1 }));
    api.categories.mockReturnValue(of([{ code: 'category-1', name: 'Ferramentas' }]));
    api.units.mockReturnValue(of([{ code: 'UN', name: 'Unidade' }]));
    api.brands.mockReturnValue(of([]));
    api.suggestCategory.mockReturnValue(of({code:'category-1',name:'Ferramentas'}));
    await TestBed.configureTestingModule({
      imports: [ProductListComponent],
      providers: [...appConfig.providers, provideHttpClientTesting(),
        { provide: ProductsApi, useValue: api },
        { provide: AuthService, useValue: { currentUser: signal({username:'dono',roles:['OWNER']}) } }],
    }).compileComponents();
  });
  it('mostra os preços e não exige nem exibe código', async () => {
    const fixture = TestBed.createComponent(ProductListComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent.replace(/\s/g, '');
    expect(text).toContain('Furadeira');
    expect(text).toContain('R$199,90');
    expect(text).not.toContain('FUR-001');
    expect(fixture.componentInstance.form.contains('code')).toBe(false);
  });
  it('salva produtos sem código manual com valores decimais exatos', () => {
    api.createComplete.mockReturnValue(of(product));
    const fixture = TestBed.createComponent(ProductListComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.startCreate();
    component.form.patchValue({ name: ' Bucha 8 mm ', categoryId: 'category-1',
      baseUnitCode: 'UN', cost: '5,00', price: '12,90' });
    const image = new File([new Uint8Array([137,80,78,71])], 'item.png', {type: 'image/png'});
    component.selectedPhoto=image;
    component.save();
    expect(api.createComplete).toHaveBeenCalledWith({
      name: 'Bucha 8 mm', description: null, categoryId: 'category-1',
      baseUnitCode: 'UN', cost: '5.00', price: '12.90',
    }, '0', image);
  });
  it('permite cadastrar sem nome, categoria, preço, custo, quantidade ou foto', () => {
    api.createComplete.mockReturnValue(of(product));
    const fixture = TestBed.createComponent(ProductListComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.startCreate();
    expect(component.form.valid).toBe(true);
    component.save();
    expect(component.formError()).toBe('');
    expect(api.createComplete).toHaveBeenCalledWith(expect.objectContaining({
      name: '', categoryId: null, baseUnitCode: '', cost: '0.00', price: '0.00',
    }), '0', undefined);
  });
  it('aceita quantidade inicial inteira ou decimal sem rejeitar números válidos', () => {
    const fixture = TestBed.createComponent(ProductListComponent);
    fixture.detectChanges();
    const control = fixture.componentInstance.form.controls.initialQuantity;
    for (const valid of ['8', '24', '1,5', '2.25', '0', '']) {
      control.setValue(valid);
      expect(control.valid, 'Quantidade deveria ser válida: ' + valid).toBe(true);
    }
    for (const invalid of ['abc', '-2', '1,12345']) {
      control.setValue(invalid);
      expect(control.invalid, 'Quantidade deveria ser inválida: ' + invalid).toBe(true);
    }
  });
  it('permite editar sem alterar o identificador interno do produto', () => {
    api.updateComplete.mockReturnValue(of(product));
    const fixture = TestBed.createComponent(ProductListComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.startEdit(product);
    component.form.patchValue({ price: '219,50' });
    component.save();
    expect(api.updateComplete).toHaveBeenCalledWith('product-1',
      expect.objectContaining({ price: '219.50', name: 'Furadeira' }), undefined);
  });
  it('cria categoria dentro do editor e atualiza a lista sem recarregar a tela', () => {
    const fixture = TestBed.createComponent(ProductListComponent); fixture.detectChanges();
    const component = fixture.componentInstance;
    component.startCreate();
    const input = document.createElement('input'); input.value = 'Ferramentas elétricas';
    component.createCategoryFromEditor(input);
    expect(input.value).toBe('Ferramentas elétricas');
    const http = TestBed.inject(HttpTestingController);
    const create = http.expectOne('/api/admin/references/categories');
    expect(create.request.method).toBe('POST');
    // Simula a leitura atualizada do servidor após a criação, não a lista antiga.
    api.categories.mockReturnValue(of([
      {code:'category-1',name:'Ferramentas'},
      {code:'category-new',name:'Ferramentas elétricas'},
    ]));
    create.flush({id:'category-new',name:'Ferramentas elétricas'});
    expect(component.form.controls.categoryId.value).toBe('category-new');
    expect(component.categories().some(c => c.code === 'category-new')).toBe(true);
    expect(input.value).toBe('');
    expect(component.referencesSuccess()).toContain('Categoria criada');
    expect(api.categories).toHaveBeenCalledTimes(2);
    expect(component.categories()).toHaveLength(2);
    http.verify();
  });

  it('mantém nome e mostra erro quando não consegue criar categoria', () => {
    const fixture = TestBed.createComponent(ProductListComponent); fixture.detectChanges();
    const component = fixture.componentInstance;
    component.startCreate();
    const input = document.createElement('input'); input.value = 'Categoria em teste';
    component.createCategoryFromEditor(input);
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/admin/references/categories').flush(
      {detail:'Categoria não autorizada'}, {status:403,statusText:'Forbidden'});
    expect(input.value).toBe('Categoria em teste');
    expect(component.referencesError()).toContain('não autorizada');
    expect(component.addingCategory()).toBe(false);
    http.verify();
  });

  it('permite consultar inativos e reativar', () => {
    api.reactivate.mockReturnValue(of(product));
    const fixture = TestBed.createComponent(ProductListComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.setFilter('inactive');
    expect(api.list).toHaveBeenLastCalledWith(0, 20, '', false, '', '', false);
    component.reactivate({ ...product, active: false });
    expect(api.reactivate).toHaveBeenCalledWith('product-1');
  });
  it('mostra erro de carregamento sem simular lista vazia', () => {
    api.list.mockReturnValue(throwError(() => ({ error: { detail: 'Serviço indisponível' } })));
    const fixture = TestBed.createComponent(ProductListComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent)
      .toContain('Serviço indisponível');
    expect(fixture.nativeElement.textContent).not.toContain('Nenhum produto encontrado');
  });
});
