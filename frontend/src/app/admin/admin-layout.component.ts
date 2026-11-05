import { Component, HostListener, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../auth/auth.service';
import { finalize, timer, switchMap, catchError, of } from 'rxjs';
import { QuickNavComponent } from './quick-nav.component';

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, MatButtonModule, QuickNavComponent],
  templateUrl: './admin-layout.component.html',
  styleUrl: './admin-layout.component.scss',
})
export class AdminLayoutComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);
  readonly failures24h = signal<number | null>(null);
  readonly isMobile = signal(typeof window !== 'undefined' && window.innerWidth <= 760);
  @HostListener('window:resize') onResize(): void { this.isMobile.set(window.innerWidth <= 760); }
  menuExpanded(): boolean { return this.isMobile() ? this.mobileMenuOpen() : this.menuOpen(); }
  constructor() {
    if (this.has('OWNER','MANAGER')) {
      timer(0,90000).pipe(
        switchMap(() => this.http.get<{failures24h:number}>('/api/admin/assistant/diagnostics')
          .pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      ).subscribe(result => this.failures24h.set(result?.failures24h ?? null));
    }
  }
  @HostListener('document:keydown.escape')
  onEscape(): void { this.mobileMenuOpen.set(false); }
  readonly logoutPending = signal(false);
  readonly logoutError = signal('');
  readonly menuOpen = signal(true);
  readonly mobileMenuOpen = signal(false);
  toggleMenu(): void {
    if (this.isMobile()) {
      this.mobileMenuOpen.update(open => !open);
    } else {
      this.menuOpen.update(open => !open);
    }
  }
  closeMobileMenu(): void { this.mobileMenuOpen.set(false); }

  has(...roles: string[]): boolean {
    return this.auth.currentUser()?.roles.some((role) => roles.includes(role)) ?? false;
  }

  logout(): void {
    if (this.logoutPending()) return;
    this.logoutPending.set(true);
    this.logoutError.set('');
    this.auth
      .logout()
      .pipe(finalize(() => this.logoutPending.set(false)))
      .subscribe({
        next: () => void this.router.navigateByUrl('/login'),
        error: () =>
          this.logoutError.set(
            'Não foi possível confirmar a saída. Verifique a conexão e tente novamente.',
          ),
      });
  }
}
