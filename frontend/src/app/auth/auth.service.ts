import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Observable, catchError, map, of, switchMap, tap } from 'rxjs';

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
  readonly currentUser = this.userState.asReadonly();

  constructor(private readonly http: HttpClient) {}

  loadCurrentUser(): Observable<CurrentUser | null> {
    return this.http.get<CurrentUser>('/api/auth/me').pipe(
      tap((user) => this.userState.set(user)),
      catchError(() => {
        this.userState.set(null);
        return of(null);
      }),
    );
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