import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from './auth.service';
export const roleGuard =
  (...roles: string[]): CanActivateFn =>
  () => {
    const auth = inject(AuthService),
      router = inject(Router);
    return auth
      .loadCurrentUser()
      .pipe(
        map((user) =>
          user && user.roles.some((role) => roles.includes(role))
            ? true
            : router.createUrlTree([
                user ? (user.roles.includes('CUSTOMER') ? '/account' : '/admin/home') : '/login',
              ]),
        ),
        catchError(() => of(router.createUrlTree(['/indisponivel']))),
      );
  };
export const adminGuard = roleGuard(
  'OWNER',
  'MANAGER',
  'SELLER',
  'CASHIER',
  'STOCKKEEPER',
  'FINANCE',
);
export const customerGuard = roleGuard('CUSTOMER');
