import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.loadCurrentUser().pipe(
    map((user) => user && user.roles.some((role) => ['OWNER', 'MANAGER', 'STOCKKEEPER'].includes(role))
      ? true
      : router.createUrlTree(['/login'])),
  );
};