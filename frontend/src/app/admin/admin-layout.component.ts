import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../auth/auth.service';
import { finalize } from 'rxjs';

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, MatButtonModule],
  templateUrl: './admin-layout.component.html',
  styleUrl: './admin-layout.component.scss',
})
export class AdminLayoutComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly logoutPending = signal(false);
  readonly logoutError = signal('');

  logout(): void {
    if (this.logoutPending()) return;
    this.logoutPending.set(true);
    this.logoutError.set('');
    this.auth.logout().pipe(finalize(() => this.logoutPending.set(false))).subscribe({
      next: () => void this.router.navigateByUrl('/login'),
      error: () => this.logoutError.set('Não foi possível confirmar a saída. Verifique a conexão e tente novamente.'),
    });
  }
}
