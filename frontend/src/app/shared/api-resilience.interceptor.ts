import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, retry, tap, throwError, timer } from 'rxjs';
import { ConnectionHealth } from './connection-health.service';

/** Nunca repetir comandos de venda, estoque, cadastro ou pagamento. */
export function canRetryRead(method: string, url: string, status: number): boolean {
  return method.toUpperCase() === 'GET'
    && url.startsWith('/api/')
    && [0, 502, 503, 504].includes(status);
}

export const apiResilienceInterceptor: HttpInterceptorFn = (request, next) => {
  if (request.method !== 'GET' || !request.url.startsWith('/api/')) return next(request);
  const health = inject(ConnectionHealth);
  return next(request).pipe(
    retry({
      count: 2,
      delay: (error: unknown, attempt: number) => {
        if (!(error instanceof HttpErrorResponse) ||
            !canRetryRead(request.method, request.url, error.status)) {
          return throwError(() => error);
        }
        health.retrying();
        return timer(attempt * 1250);
      },
    }),
    tap(event => { if (event instanceof HttpResponse) health.healthy(); }),
    catchError(error => {
      if (error instanceof HttpErrorResponse &&
          canRetryRead(request.method, request.url, error.status)) health.unavailable();
      return throwError(() => error);
    }),
  );
};
