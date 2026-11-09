import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, it, beforeEach, afterEach, expect, vi } from 'vitest';
import { BarcodeSearchComponent } from './barcode-search.component';
import { BarcodeApi, BarcodeItem } from './barcode-api.service';

const item: BarcodeItem = {
  productId: 'p1', barcode: 'LF0000000123', name: 'Bucha 6 mm',
  unit: 'UN', price: '0.50', physical: '50', available: '50', minimumQuantity: '5',
  quantityScale: 0, quantityStep: '1', revision: 1, active: true,
};

describe('Pesquisa assistida de etiquetas', () => {
  const api = { search: vi.fn(), lookup: vi.fn() };

  beforeEach(async () => {
    vi.resetAllMocks();
    vi.useFakeTimers();
    api.search.mockReturnValue(of([item]));
    api.lookup.mockReturnValue(of(item));
    await TestBed.configureTestingModule({
      imports: [BarcodeSearchComponent],
      providers: [{ provide: BarcodeApi, useValue: api }],
    }).compileComponents();
  });
  afterEach(() => { vi.useRealTimers(); });

  it('sugere pelo final sem selecionar nem movimentar automaticamente', async () => {
    const fixture = TestBed.createComponent(BarcodeSearchComponent);
    fixture.detectChanges();
    const found = vi.fn();
    fixture.componentInstance.picked.subscribe(found);
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
    input.value = '123';
    input.dispatchEvent(new Event('input'));
    await vi.advanceTimersByTimeAsync(200);
    fixture.detectChanges();
    expect(api.search).toHaveBeenCalledWith('123', '');
    expect(found).not.toHaveBeenCalled();
    const choice: HTMLButtonElement = fixture.nativeElement.querySelector('[role="option"]');
    expect(choice.textContent).toContain('Bucha 6 mm');
    choice.click();
    expect(found).toHaveBeenCalledWith(item);
    fixture.destroy();
  });

  it('um bip completo mais Enter usa consulta exata', () => {
    const fixture = TestBed.createComponent(BarcodeSearchComponent);
    fixture.detectChanges();
    const found = vi.fn();
    fixture.componentInstance.picked.subscribe(found);
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
    input.value = 'lf0000000123';
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    expect(api.lookup).toHaveBeenCalledWith('LF0000000123', '');
    expect(found).toHaveBeenCalledWith(item);
    fixture.destroy();
  });
});
