import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../auth/auth.service';
import { ActionStatusComponent } from './action-status.component';
import { Row, createAction, label, detail } from './ui';
@Component({
  standalone: true,
  imports: [FormsModule, ActionStatusComponent],
  templateUrl: './users.component.html',
  styleUrl: './ui.scss',
})
export class UsersComponent {
  private readonly http = inject(HttpClient);
  readonly auth = inject(AuthService);
  readonly action = createAction();
  readonly users = signal<Row[]>([]);
  readonly error = signal('');
  readonly label = label;
  readonly roles = ['OWNER', 'MANAGER', 'SELLER', 'CASHIER', 'STOCKKEEPER', 'FINANCE', 'CUSTOMER'];
  open = false;
  editing: string | null = null;
  form = { username: '', email: '', password: '', enabled: true, roles: ['SELLER'] };
  constructor() {
    this.load();
  }
  load() {
    this.http
      .get<Row[]>('/api/admin/users')
      .subscribe({ next: (r) => this.users.set(r), error: (e) => this.error.set(detail(e)) });
  }
  edit(user?: Row) {
    this.editing = user?.id ?? null;
    this.form = {
      username: user?.['username'] ?? '',
      email: user?.['email'] ?? '',
      password: '',
      enabled: user?.['enabled'] ?? true,
      roles: user?.['roles'] ? [...user['roles']] : ['SELLER'],
    };
    this.open = true;
  }
  save() {
    const body = { ...this.form, password: this.form.password || null };
    this.action.submit(
      '/api/admin/users' + (this.editing ? '/' + this.editing : ''),
      body,
      () => {
        this.open = false;
        this.form.password = '';
        this.load();
      },
      this.editing ? 'PUT' : 'POST',
    );
  }
}
