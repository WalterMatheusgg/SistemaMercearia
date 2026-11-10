import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, shareReplay, switchMap, tap, throwError } from 'rxjs';

export interface CurrentUser {
  username: string;
  roles: string[];
}

interface Credentials {
  username: string;
  password: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly userState = signal<CurrentUser | null>(null);
  private loadingUser: Observable<CurrentUser | null> | null = null;
  readonly currentUser = this.userState.asReadonly();

  constructor(private readonly http: HttpClient) {}

  loadCurrentUser(): Observable<CurrentUser | null> {
    // Guards pai/filho fazem a mesma consulta: compartilhar reduz cold starts.
    if (this.loadingUser) return this.loadingUser;
    const request = this.http.get<CurrentUser>('/api/auth/me').pipe(
      tap(user => this.userState.set(user)),
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401 || error.status === 403) {
          this.userState.set(null);
          return of(null);
        }
        // Atraso ou falha de gateway não significa que a sessão foi perdida.
        return throwError(() => error);
      }),
      finalize(() => { this.loadingUser = null; }),
      shareReplay({ bufferSize: 1, refCount: true }),
    );
    this.loadingUser = request;
    return request;
  }

  login(credentials: Credentials): Observable<CurrentUser> {
    return this.http.get('/api/auth/csrf').pipe(
      switchMap(() => this.http.post<CurrentUser>('/api/auth/login', credentials)),
      tap((user) => this.userState.set(user)),
      switchMap((user) => this.http.get('/api/auth/csrf').pipe(map(() => user))),
    );
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', {}).pipe(
      tap(() => this.userState.set(null)),
    );
  }
}