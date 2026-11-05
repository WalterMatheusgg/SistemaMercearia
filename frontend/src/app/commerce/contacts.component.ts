import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { HttpClient } from '@angular/common/http';
import { ActionStatusComponent } from './action-status.component';
import { Row, Page, createAction, money, date, label, detail } from './ui';
@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, ActionStatusComponent],
  templateUrl: './contacts.component.html',
  styleUrls: ['./ui.scss','./contacts.component.scss'],
})
export class ContactsComponent {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  canManageMaterials(): boolean { return this.auth.currentUser()?.roles.some(r=>['OWNER','MANAGER','STOCKKEEPER'].includes(r)) ?? false; }
  readonly action = createAction();
  readonly rows = signal<Row[]>([]);
  readonly selected = signal<Row | null>(null);
  readonly error = signal('');
  readonly loading = signal(false);
  readonly kind = this.route.snapshot.data['kind'] as string;
  readonly explanation = this.kind === 'SUPPLIER' ? 'Empresas que fornecem materiais. Quantidades e movimentações ficam em Estoque.' : 'Contatos de clientes e seu histórico.';

  readonly title = this.kind === 'CUSTOMER' ? 'Clientes' : 'Fornecedores';
  readonly money = money;
  readonly date = date;
  readonly label = label;
  query = '';
  page = 0;
  total = 0;
  editing: string | null = null;
  open = false;
  form = { name: '', email: '', phone: '', document: '', address: '', active: true };
  constructor() {
    this.load();
  }
  load() {
    this.loading.set(true);
    this.http
      .get<Page>('/api/admin/contacts/' + this.kind, {
        params: { query: this.query, page: this.page, size: 20 },
      })
      .subscribe({
        next: (r) => {
          this.rows.set(r.content);
          this.total = r.totalElements;
          this.loading.set(false);
        },
        error: (e) => {
          this.error.set(detail(e));
          this.loading.set(false);
        },
      });
  }
  edit(row?: Row) {
    this.editing = row?.id ?? null;
    this.form = {
      name: row?.['name'] ?? '',
      email: row?.['email'] ?? '',
      phone: row?.['phone'] ?? '',
      document: row?.['document'] ?? '',
      address: row?.['address'] ?? '',
      active: row?.['active'] ?? true,
    };
    this.open = true;
  }
  save() {
    this.action.submit(
      '/api/admin/contacts/' + this.kind + (this.editing ? '/' + this.editing : ''),
      this.form,
      () => {
        this.open = false;
        this.load();
      },
      this.editing ? 'PUT' : 'POST',
    );
  }
  history(row: Row) {
    this.http
      .get<Row>('/api/admin/contacts/' + this.kind + '/' + row.id)
      .subscribe({ next: (r) => this.selected.set(r), error: (e) => this.error.set(detail(e)) });
  }
}
