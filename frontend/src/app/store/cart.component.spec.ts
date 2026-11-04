import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { CartComponent } from './cart.component';

describe('Carrinho: proteção contra cálculo antigo', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CartComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    })
      .overrideComponent(CartComponent, {
        set: { template: '', imports: [] },
      })
      .compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('ignora o resultado de um cálculo recebido após alterar a quantidade', () => {
    const fixture = TestBed.createComponent(CartComponent);
    const component = fixture.componentInstance;
    http.expectOne('/api/catalog/store').flush({
      deliveryEnabled: false,
      deliveryFee: '0.00',
    });
    component.cart.items.set([
      {
        productId: 'product-1',
        code: 'TESTE',
        name: 'Material',
        unit: 'UN',
        quantity: '2',
        price: '2.00',
      },
    ]);

    component.calculate();
    http.expectOne('/api/auth/csrf').flush({});
    const oldCalculation = http.expectOne('/api/catalog/preview');
    expect(oldCalculation.request.body.items[0].quantity).toBe('2');

    component.change('product-1', '3');
    oldCalculation.flush({
      total: '4.00',
      subtotal: '4.00',
      deliveryFee: '0.00',
      items: [],
    });

    expect(component.preview()).toBeNull();
    expect(component.calculating).toBe(false);

    component.calculate();
    http.expectOne('/api/auth/csrf').flush({});
    const newCalculation = http.expectOne('/api/catalog/preview');
    expect(newCalculation.request.body.items[0].quantity).toBe('3');
    newCalculation.flush({
      total: '6.00',
      subtotal: '6.00',
      deliveryFee: '0.00',
      items: [],
    });

    expect(component.preview()?.total).toBe('6.00');
    fixture.destroy();
  });
});
