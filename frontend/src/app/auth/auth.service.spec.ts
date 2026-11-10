import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AuthService } from './auth.service';

describe('sessão em memória para navegação rápida', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('compartilha GET /me para dois guards simultâneos', () => {
    let first = '';
    let second = '';
    service.loadCurrentUser().subscribe(user => { first = user?.username ?? ''; });
    service.loadCurrentUser().subscribe(user => { second = user?.username ?? ''; });
    const requests = http.match('/api/auth/me');
    expect(requests).toHaveLength(1);
    requests[0].flush({ username: 'operador', roles: ['SELLER'] });
    expect(first).toBe('operador');
    expect(second).toBe('operador');
  });

  it('usa a sessão recém-confirmada sem pedir outra rede ao trocar de tela', () => {
    let loggedIn = false;
    service.login({ username: 'operador', password: 'senha-de-teste' }).subscribe(() => { loggedIn = true; });
    http.expectOne('/api/auth/csrf').flush({});
    http.expectOne('/api/auth/login').flush({ username: 'operador', roles: ['SELLER'] });
    http.expectOne('/api/auth/csrf').flush({});
    expect(loggedIn).toBe(true);

    let user = '';
    service.loadCurrentUser().subscribe(value => { user = value?.username ?? ''; });
    expect(user).toBe('operador');
    http.expectNone('/api/auth/me');
  });

  it('invalida o cache após sair e volta a consultar a autenticação', () => {
    service.loadCurrentUser().subscribe();
    http.expectOne('/api/auth/me').flush({ username: 'operador', roles: ['SELLER'] });
    service.logout().subscribe();
    http.expectOne('/api/auth/logout').flush(null);
    service.loadCurrentUser().subscribe(value => expect(value).toBeNull());
    http.expectOne('/api/auth/me').flush({ detail: 'Não autenticado' }, {status: 401, statusText: 'Unauthorized'});
  });
});
