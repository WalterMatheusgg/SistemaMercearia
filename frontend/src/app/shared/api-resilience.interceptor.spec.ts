import { describe, expect, it } from 'vitest';
import { canRetryRead } from './api-resilience.interceptor';

describe('reconexão segura de leituras', () => {
  it('tenta novamente apenas GET de API após falha temporária', () => {
    for (const status of [0, 502, 503, 504]) {
      expect(canRetryRead('GET', '/api/auth/csrf', status)).toBe(true);
    }
  });
  it('nunca repete registros de venda, caixa ou estoque', () => {
    for (const method of ['POST','PUT','PATCH','DELETE']) {
      expect(canRetryRead(method, '/api/admin/sales', 504)).toBe(false);
      expect(canRetryRead(method, '/api/admin/inventory', 502)).toBe(false);
    }
  });
  it('não repete autenticação negada ou erros de negócio', () => {
    for (const status of [400,401,403,404,409,422,429,500]) {
      expect(canRetryRead('GET', '/api/admin/reports', status)).toBe(false);
    }
    expect(canRetryRead('GET', '/loja.svg', 503)).toBe(false);
  });
});
