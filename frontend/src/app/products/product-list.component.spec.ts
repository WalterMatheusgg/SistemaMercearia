import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { appConfig } from '../app.config';
import { ProductListComponent } from './product-list.component';
import { Product, ProductsApi } from './products-api.service';

const product: Product = {
  id: 'product-1', code: 'FUR-001', name: 'Furadeira', description: null,
  categoryId: 'category-1', categoryName: 'Ferramentas', baseUnitCode: 'UN',
  baseUnitName: 'Unidade', cost: '120.00', price: '199.90', active: true,
  createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
};

describe('Cadastro simples de produtos', () => {
  const api = {
    list: vi.fn(), categories: vi.fn(), units: vi.fn(),
    create: vi.fn(), update: vi.fn(), createComplete: vi.fn(), updateComplete: vi.fn(), deactivate: vi.fn(), reactivate: vi.fn(),
  };
  beforeEach(async () => {
    vi.resetAllMocks();
    api.list.mockReturnValue(of({ content: [product], page: 0, size: 20, totalElements: 1, totalPages: 1 }));
    api.categories.mockReturnValue(of([{ code: 'category-1', name: 'Ferramentas' }]));
    api.units.mockReturnValue(of([{ code: 'UN', name: 'Unidade' }]));
    await TestBed.configureTestingModule({
      imports: [ProductListComponent],
      providers: [...appConfig.providers, { provide: ProductsApi, useValue: api }],
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
  it('permite nome/categoria/valores vazios, exigindo apenas uma foto', () => {
    api.createComplete.mockReturnValue(of(product));
    const fixture = TestBed.createComponent(ProductListComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.startCreate();
    expect(component.form.valid).toBe(true);
    component.save();
    expect(component.formError()).toContain('foto');
    const image = new File([new Uint8Array([137,80,78,71])], 'item.png', {type: 'image/png'});
    component.selectedPhoto=image;
    component.save();
    expect(api.createComplete).toHaveBeenCalledWith(expect.objectContaining({
      name: '', categoryId: null, baseUnitCode: '', cost: '0.00', price: '0.00',
    }), '0', image);
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
  it('permite consultar inativos e reativar', () => {
    api.reactivate.mockReturnValue(of(product));
    const fixture = TestBed.createComponent(ProductListComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.setFilter('inactive');
    expect(api.list).toHaveBeenLastCalledWith(0, 20, '', false);
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
