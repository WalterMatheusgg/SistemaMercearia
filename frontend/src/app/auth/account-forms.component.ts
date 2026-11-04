import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from './auth.service';
import { StoreNavComponent } from '../store/store-nav.component';
import { ActionStatusComponent } from '../commerce/action-status.component';
import { createAction, detail } from '../commerce/ui';
@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, StoreNavComponent, ActionStatusComponent],
  templateUrl: './account-forms.component.html',
  styleUrls: ['../commerce/ui.scss', '../store/store.scss'],
})
export class AccountFormsComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  readonly action = createAction();
  readonly finished = signal(false);
  readonly mode = this.route.snapshot.data['mode'] as string;
  readonly title = (
    {
      register: 'Criar conta de cliente',
      forgot: 'Recuperar acesso',
      reset: 'Redefinir senha',
      verify: 'Verificar e-mail',
    } as Record<string, string>
  )[this.mode];
  username = '';
  email = '';
  name = '';
  phone = '';
  password = '';
  confirmPassword = '';
  token = this.route.snapshot.queryParamMap.get('token') ?? '';
  submit() {
    if (['register', 'reset'].includes(this.mode) && this.password !== this.confirmPassword) {
      this.action.error.set('As senhas precisam ser iguais.');
      return;
    }
    if (this.mode === 'register') {
      this.action.submit(
        '/api/auth/register',
        {
          username: this.username,
          email: this.email,
          name: this.name,
          phone: this.phone,
          password: this.password,
        },
        () => {
          this.auth.login({ username: this.username, password: this.password }).subscribe({
            next: () => {
              this.password = '';
              this.confirmPassword = '';
              void this.router.navigateByUrl('/account');
            },
            error: (e) => {
              this.action.error.set('Conta criada. Entre pela tela de login. ' + detail(e));
              this.finished.set(true);
            },
          });
        },
      );
    } else if (this.mode === 'forgot')
      this.action.submit('/api/auth/password/request', { username: this.username }, (r) => {
        this.action.message.set(r.message);
        this.finished.set(true);
      });
    else if (this.mode === 'reset')
      this.action.submit(
        '/api/auth/password/reset',
        { token: this.token, password: this.password },
        (r) => {
          this.password = '';
          this.confirmPassword = '';
          this.action.message.set(r.message);
          this.finished.set(true);
        },
      );
    else
      this.action.submit('/api/auth/email/verify', { token: this.token }, (r) => {
        this.action.message.set(r.message);
        this.finished.set(true);
      });
  }
}
