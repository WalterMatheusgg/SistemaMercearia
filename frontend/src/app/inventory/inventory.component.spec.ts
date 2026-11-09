import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { InventoryComponent } from './inventory.component';
import { BarcodeApi } from './barcode-api.service';
import { InventoryApi, InventoryBalance, InventoryMovement } from './inventory-api.service';
import { AuthService } from '../auth/auth.service';

const balance: InventoryBalance = {
  productId: 'product-1', locationId: 'location-1', code: 'PAR-01', name: 'Parafuso', unit: 'UN',
  quantityScale: 0, quantityStep: '1', physical: '50', reserved: '0', unavailable: '0', available: '50',
  minimumQuantity: '10', revision: 1, status: 'NORMAL', active: true,
};
const movement: InventoryMovement = {
  id: 'movement-1', productId: 'product-1', locationId: 'location-1', code: 'PAR-01', productName: 'Parafuso', unit: 'UN',
  kind: 'EXIT', quantity: '15', delta: '-15', balanceBefore: '50', balanceAfter: '35', minimumBefore: '10', minimumAfter: '10',
  reason: 'Saída conferida', actorName: 'dono', occurredAt: '2026-11-04T12:00:00Z', reversesMovementId: null, reversed: false,
};

describe('Operação de estoque', () => {
  const barcodeApi = { lookup: vi.fn(), product: vi.fn() };
  const api = { locations: vi.fn(), categories: vi.fn(), replenishment: vi.fn(), balances: vi.fn(), history: vi.fn(), execute: vi.fn() };
  beforeEach(async () => {
    vi.resetAllMocks();
    barcodeApi.lookup.mockReturnValue(of({ productId: 'product-1', barcode: 'LF0000000001', name: 'Parafuso', unit: 'UN',
      price: '2', physical: '50', available: '50', minimumQuantity: '10', quantityScale: 0,
      quantityStep: '1', revision: 1, active: true }));
    api.locations.mockReturnValue(of([{ id: 'location-1', name: 'Principal' }]));
    api.replenishment.mockReturnValue(of([]));
    api.categories.mockReturnValue(of([
      {id:'category-1',name:'Fixação'},
      {id:'category-2',name:'Hidráulica'},
    ]));
    api.balances.mockReturnValue(of({ content: [balance], totalElements: 1 }));
    api.history.mockReturnValue(of({ content: [], totalElements: 0 }));
    await TestBed.configureTestingModule({ imports: [InventoryComponent], providers: [
      provideRouter([]),
      { provide: InventoryApi, useValue: api },
      { provide: BarcodeApi, useValue: barcodeApi },
      { provide: AuthService, useValue: { currentUser: signal({ username: 'dono', roles: ['OWNER'] }) } },
    ] }).compileComponents();
  });

  it('filtra materiais por categoria no servidor e reseta a paginação', () => {
    const fixture = TestBed.createComponent(InventoryComponent); fixture.detectChanges();
    const component = fixture.componentInstance;
    expect(component.categories().length).toBe(2);
    component.page.set(3);
    component.setCategory('category-1');
    expect(component.page()).toBe(0);
    expect(component.categoryId()).toBe('category-1');
    expect(api.balances).toHaveBeenLastCalledWith(
      'location-1', '', false, false, 0, 'category-1');
    component.setCategory('');
    expect(api.balances).toHaveBeenLastCalledWith(
      'location-1', '', false, false, 0, '');
  });

  it('identifica material bipado sem movimentar e permite escolher a operação', () => {
    const fixture = TestBed.createComponent(InventoryComponent); fixture.detectChanges();
    const component = fixture.componentInstance;
    component.scanBarcode(' LF0000000001 ');
    expect(barcodeApi.lookup).toHaveBeenCalledWith('LF0000000001', 'location-1');
    expect(component.scanned()?.name).toBe('Parafuso');
    expect(api.execute).not.toHaveBeenCalled();
    component.openScanned('EXIT');
    expect(component.formOpen()).toBe(true);
    expect(component.selected()?.productId).toBe('product-1');
    expect(api.execute).not.toHaveBeenCalled();
  });

  it('registra saída e atualiza a lista com o saldo retornado pelo servidor', () => {
    api.execute.mockReturnValue(of(movement));
    const fixture = TestBed.createComponent(InventoryComponent); fixture.detectChanges();
    const component = fixture.componentInstance;
    component.open(balance, 'EXIT');
    component.form.patchValue({ quantity: '15', reason: 'Saída conferida' });
    api.balances.mockReturnValue(of({ content: [{ ...balance, physical: '35', available: '35', revision: 2 }], totalElements: 1 }));
    component.save(); fixture.detectChanges();
    expect(api.execute).toHaveBeenCalledWith(expect.objectContaining({ kind: 'EXIT', quantity: '15', productId: 'product-1' }), expect.any(String));
    expect(component.rows()[0].physical).toBe('35');
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('35 UN');
  });

  it('repete a mesma chave e o mesmo conteúdo quando o resultado ficou incerto', () => {
    api.execute.mockReturnValueOnce(throwError(() => ({ status: 0 }))).mockReturnValueOnce(of(movement));
    const fixture = TestBed.createComponent(InventoryComponent); fixture.detectChanges();
    const component = fixture.componentInstance;
    component.open(balance, 'EXIT'); component.form.patchValue({ quantity: '15', reason: 'Saída conferida' });
    component.save();
    expect(component.uncertain()).toBe(true);
    expect(component.form.disabled).toBe(true);
    component.save();
    expect(api.execute.mock.calls[1]).toEqual(api.execute.mock.calls[0]);
    expect(component.uncertain()).toBe(false);
  });

  it('preserva o formulário e mostra um conflito sem informar sucesso', () => {
    api.execute.mockReturnValue(throwError(() => ({ status: 409, error: { detail: 'Quantidade insuficiente' } })));
    const fixture = TestBed.createComponent(InventoryComponent); fixture.detectChanges();
    const component = fixture.componentInstance;
    component.open(balance, 'EXIT'); component.form.patchValue({ quantity: '70', reason: 'Saída conferida' });
    component.save(); fixture.detectChanges();
    expect(component.formOpen()).toBe(true);
    expect(component.message()).toBe('');
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Quantidade insuficiente');
    expect(component.rows()[0].physical).toBe('50');
  });
});
