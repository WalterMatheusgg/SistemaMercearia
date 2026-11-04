import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../auth/auth.service';
import { RouterLink } from '@angular/router';
import { ActionStatusComponent } from './action-status.component';
import { createAction, label, date, detail } from './ui';
@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, ActionStatusComponent],
  templateUrl: './notifications.component.html',
  styleUrl: './ui.scss',
})
export class NotificationsComponent {
  private readonly http = inject(HttpClient);
  readonly auth = inject(AuthService);
  readonly action = createAction();
  readonly state = signal<any | null>(null);
  readonly error = signal('');
  readonly label = label;
  readonly date = date;
  form = { saleCompleted: false, stockLow: false, dailySummary: false };
  constructor() {
    this.load();
  }
  management() {
    return this.auth.currentUser()?.roles.some((r) => ['OWNER', 'MANAGER'].includes(r));
  }
  load() {
    this.http.get<any>('/api/admin/notifications').subscribe({
      next: (r) => {
        this.state.set(r);
        this.form = {
          saleCompleted: r.saleCompleted,
          stockLow: r.stockLow,
          dailySummary: r.dailySummary,
        };
      },
      error: (e) => this.error.set(detail(e)),
    });
  }
  save() {
    this.action.submit('/api/admin/notifications', this.form, () => this.load(), 'PUT');
  }
  verify() {
    this.action.submit('/api/account/email/request-verification', {}, () =>
      this.action.message.set(
        'Solicitação registrada. Confira a caixa de entrada ou o Mailpit local.',
      ),
    );
  }
}
