import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { InventoryApi, MovementCommand } from './inventory-api.service';

describe('API de estoque', () => {
  it('envia a chave de idempotência e mantém a quantidade como string decimal', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const command: MovementCommand = { productId: 'p', locationId: 'l', kind: 'ENTRY', quantity: '1.25',
      reason: 'Recebimento', expectedRevision: null, reversesMovementId: null };
    TestBed.inject(InventoryApi).execute(command, 'operation-1').subscribe();
    const request = http.expectOne('/api/admin/inventory/movements');
    expect(request.request.headers.get('Idempotency-Key')).toBe('operation-1');
    expect(request.request.body.quantity).toBe('1.25');
    request.flush({}); http.verify();
  });
});
