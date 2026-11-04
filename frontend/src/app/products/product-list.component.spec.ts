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

describe('Cadastro de produtos', () => {
  const api = {
    list: vi.fn(), categories: vi.fn(), units: vi.fn(),
    create: vi.fn(), update: vi.fn(), deactivate: vi.fn(),
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

  it('renderiza um produto com preço em reais e locale brasileiro', async () => {
    const fixture = TestBed.createComponent(ProductListComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent.replace(/\s/g, '');
    expect(text).toContain('Furadeira');
    expect(text).toContain('R$199,90');
  });

  it('envia valores decimais sem converter para ponto flutuante', () => {
    api.create.mockReturnValue(of(product));
    const fixture = TestBed.createComponent(ProductListComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.startCreate();
    component.form.setValue({ code: ' FUR-001 ', name: ' Furadeira ', description: '',
      categoryId: 'category-1', baseUnitCode: 'UN', cost: '120,00', price: '199,90' });
    component.save();
    expect(api.create).toHaveBeenCalledWith(expect.objectContaining({
      code: 'FUR-001', name: 'Furadeira', cost: '120.00', price: '199.90',
    }));
  });

  it('mostra falha de carregamento sem representar uma lista vazia como sucesso', () => {
    api.list.mockReturnValue(throwError(() => ({ error: { detail: 'Serviço indisponível' } })));
    const fixture = TestBed.createComponent(ProductListComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Serviço indisponível');
    expect(fixture.nativeElement.textContent).not.toContain('Nenhum produto encontrado');
  });
});
