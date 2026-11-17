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
  form = { name: '', email: '', phone: '', document: '', address: '', active: true, representativeName: '', whatsapp: '', city: '', products: '', brands: '', minimumOrder: '', quantityDiscount: '', paymentTerms: '', deliveryTime: '', freightTerms: '', returnPolicy: '', warrantyPolicy: '' };
  constructor() {
    this.load();
  }
  load() {
    this.loading.set(true);
    this.http
      .get<Page>('/api/admin/contacts/' + this.kind, {
        params: { query: this.query, page: this.page, size: 100 },
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
      representativeName: row?.['representativeName'] ?? '', whatsapp: row?.['whatsapp'] ?? '', city: row?.['city'] ?? '', products: row?.['products'] ?? '', brands: row?.['brands'] ?? '', minimumOrder: row?.['minimumOrder'] ?? '', quantityDiscount: row?.['quantityDiscount'] ?? '', paymentTerms: row?.['paymentTerms'] ?? '', deliveryTime: row?.['deliveryTime'] ?? '', freightTerms: row?.['freightTerms'] ?? '', returnPolicy: row?.['returnPolicy'] ?? '', warrantyPolicy: row?.['warrantyPolicy'] ?? '',
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

  exportSuppliers(): void {
    if (this.kind !== 'SUPPLIER') return;
    const headers = ['Empresa','Representante comercial','Telefone','WhatsApp','E-mail','Cidade','Endereço','Produtos','Marcas','Pedido mínimo','Preços e descontos por quantidade','Condições de pagamento','Prazo de entrega','Valor/condições do frete','Política de troca','Garantia','Ativo'];
    const keys = ['name','representativeName','phone','whatsapp','email','city','address','products','brands','minimumOrder','quantityDiscount','paymentTerms','deliveryTime','freightTerms','returnPolicy','warrantyPolicy','active'];
    const escapeCell = (value: unknown) => '"' + String(value ?? '').replace(/"/g, '""') + '"';
    const lines = [headers, ...this.rows().map(row => keys.map(key => key === 'active' ? (row[key] ? 'Sim' : 'Não') : row[key]))];
    const csv = '\uFEFF' + lines.map(line => line.map(escapeCell).join(';')).join('\r\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = 'fornecedores-merceria.csv';
    anchor.click();
    URL.revokeObjectURL(url);
  }

  history(row: Row) {
    this.http
      .get<Row>('/api/admin/contacts/' + this.kind + '/' + row.id)
      .subscribe({ next: (r) => this.selected.set(r), error: (e) => this.error.set(detail(e)) });
  }
}
