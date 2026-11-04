import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AdminLayoutComponent } from './admin-layout.component';
import { AuthService } from '../auth/auth.service';

describe('Saída do sistema', () => {
  const auth = { currentUser: signal({ username: 'dono', roles: ['OWNER'] }), logout: vi.fn() };

  beforeEach(async () => {
    vi.resetAllMocks();
    await TestBed.configureTestingModule({
      imports: [AdminLayoutComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();
  });

  it('mantém o contexto e informa quando não é possível confirmar o logout', () => {
    auth.logout.mockReturnValue(throwError(() => new Error('Falha de rede')));
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigateByUrl');
    const fixture = TestBed.createComponent(AdminLayoutComponent);
    fixture.componentInstance.logout();
    fixture.detectChanges();
    expect(navigate).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Não foi possível confirmar a saída');
    expect(fixture.componentInstance.logoutPending()).toBe(false);
  });

  it('abre o login depois da confirmação do servidor', () => {
    auth.logout.mockReturnValue(of(undefined));
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(AdminLayoutComponent);
    fixture.componentInstance.logout();
    expect(navigate).toHaveBeenCalledWith('/login');
    expect(fixture.componentInstance.logoutError()).toBe('');
  });
});
