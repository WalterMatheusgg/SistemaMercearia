import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { createAction } from './ui';
@Component({ standalone: true, template: '' })
class Harness {
  readonly action = createAction();
}
describe('Confirmação de operações comerciais', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Harness],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('mantém chave e corpo ao confirmar um pagamento após perda de conexão', () => {
    const fixture = TestBed.createComponent(Harness),
      action = fixture.componentInstance.action,
      done = vi.fn();
    action.submit('/api/admin/finance/title/settle', { amount: '30.00', method: 'PIX' }, done);
    http.expectOne('/api/auth/csrf').flush({});
    const first = http.expectOne('/api/admin/finance/title/settle'),
      key = first.request.headers.get('Idempotency-Key');
    first.error(new ProgressEvent('error'));
    expect(action.uncertain()).toBe(true);
    expect(done).not.toHaveBeenCalled();
    action.retry();
    http.expectOne('/api/auth/csrf').flush({});
    const retry = http.expectOne('/api/admin/finance/title/settle');
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    expect(retry.request.body).toEqual({ amount: '30.00', method: 'PIX' });
    retry.flush({ id: 'payment-1' });
    expect(action.uncertain()).toBe(false);
    expect(done).toHaveBeenCalledOnce();
  });
  it('não envia outra escrita enquanto a primeira está em andamento', () => {
    const fixture = TestBed.createComponent(Harness),
      action = fixture.componentInstance.action;
    action.submit('/api/admin/sales', { items: [] }, vi.fn());
    action.submit('/api/admin/sales', { items: [] }, vi.fn());
    http.expectOne('/api/auth/csrf').flush({});
    const request = http.expectOne('/api/admin/sales');
    request.flush({ id: 'sale-1' });
    expect(action.busy()).toBe(false);
  });
  it('permite corrigir uma operação recusada e usa nova chave', () => {
    const fixture = TestBed.createComponent(Harness),
      action = fixture.componentInstance.action,
      done = vi.fn();
    action.submit('/api/admin/cash/adjust', { amount: '100.00' }, done);
    http.expectOne('/api/auth/csrf').flush({});
    const first = http.expectOne('/api/admin/cash/adjust'),
      key = first.request.headers.get('Idempotency-Key');
    first.flush({ detail: 'Dinheiro insuficiente' }, { status: 409, statusText: 'Conflict' });
    expect(action.uncertain()).toBe(false);
    expect(action.error()).toBe('Dinheiro insuficiente');
    expect(done).not.toHaveBeenCalled();
    action.submit('/api/admin/cash/adjust', { amount: '10.00' }, done);
    http.expectOne('/api/auth/csrf').flush({});
    const next = http.expectOne('/api/admin/cash/adjust');
    expect(next.request.headers.get('Idempotency-Key')).not.toBe(key);
    next.flush({ id: 'entry-1' });
  });
});
