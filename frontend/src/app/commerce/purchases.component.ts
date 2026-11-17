import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { ActionStatusComponent } from './action-status.component';
import {
  Row,
  Page,
  createAction,
  money,
  quantity,
  date,
  label,
  decimal,
  detail,
  today,
} from './ui';
@Component({
  standalone: true,
  imports: [FormsModule, ActionStatusComponent],
  templateUrl: './purchases.component.html',
  styleUrl: './ui.scss',
})
export class PurchasesComponent {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  readonly auth = inject(AuthService);
  readonly action = createAction();
  readonly rows = signal<Row[]>([]);
  readonly products = signal<Row[]>([]);
  readonly suppliers = signal<Row[]>([]);
  readonly offers = signal<Row[]>([]);
  readonly comparisons = signal<Row[]>([]);
  readonly selected = signal<Row | null>(null);
  readonly error = signal('');
  readonly money = money;
  readonly quantity = quantity;
  readonly date = date;
  readonly label = label;
  page = 0;
  total = 0;
  open = false;
  query = '';
  supplierQuery = '';
  supplierId = '';
  productId = '';
  amount = '1';
  cost = '';
  dueDate = today();
  note = '';
  readonly suggestedNotice = signal('');
  receiptNote = '';
  cancelReason = '';
  lines: { productId: string; name: string; quantity: string; unitCost: string }[] = [];
  offerEditing: string | null = null;
  offerOpen = false;
  offerForm = { supplierId: '', productId: '', brand: '', supplierProductCode: '',
    unitCost: '', minimumOrderQuantity: '1', quotedAt: today(), validUntil: '',
    deliveryDays: '', freightCost: '0', notes: '', active: true };
  offerTiers: { minimumQuantity: string; unitCost: string }[] = [];
  compareProductId = '';
  compareQuantity = '1';
  receiveLines: {
    itemId: string;
    name: string;
    unit: string;
    ordered: string;
    received: string;
    quantity: string;
  }[] = [];
  constructor() {
    this.load();
    this.searchProducts();
    this.searchSuppliers();
    this.loadOffers();
    const params = this.route.snapshot.queryParamMap;
    const suggestedProductId = params.get('produto');
    if (suggestedProductId && this.management()) {
      this.open = true;
      this.productId = suggestedProductId;
      this.amount = params.get('quantidade') || '1';
      this.cost = params.get('custo') || '';
      this.supplierId = params.get('fornecedor') || '';
      this.suggestedNotice.set(
        'Sugestão de reposição carregada. Confira fornecedor, custo e quantidade. '
        + 'Clique em Adicionar item e confirme o pedido somente após revisar tudo.');
      this.http.get<Row>('/api/admin/products/' + encodeURIComponent(suggestedProductId))
        .subscribe({
          next: product => {
            this.query = product['name'];
            this.searchProducts();
          },
          error: () => this.error.set('O produto sugerido não pôde ser consultado. Selecione-o manualmente.'),
        });
    }
  }
  management() {
    return this.auth.currentUser()?.roles.some((r) => ['OWNER', 'MANAGER'].includes(r));
  }
  load() {
    this.http.get<Page>('/api/admin/purchases', { params: { page: this.page } }).subscribe({
      next: (r) => {
        this.rows.set(r.content);
        this.total = r.totalElements;
      },
      error: (e) => this.error.set(detail(e)),
    });
  }
  searchProducts() {
    this.http.get<Page>('/api/admin/catalog', { params: { query: this.query } }).subscribe({
      next: (r) => this.products.set(r.content),
      error: (e) => this.error.set(detail(e)),
    });
  }
  searchSuppliers() {
    this.http
      .get<Page>('/api/admin/contacts/SUPPLIER', {
        params: { query: this.supplierQuery, size: 100 },
      })
      .subscribe({
        next: (r) => this.suppliers.set(r.content.filter((c) => c['active'])),
        error: (e) => this.error.set(detail(e)),
      });
  }
  loadOffers() {
    this.http.get<{content: Row[]}>('/api/admin/supplier-offers', { params: { includeInactive: true } })
      .subscribe({ next: r => this.offers.set(r.content), error: e => this.error.set(detail(e)) });
  }
  compareOffers() {
    if (!this.compareProductId || !/^\d+([.,]\d{1,4})?$/.test(this.compareQuantity) || Number(decimal(this.compareQuantity)) <= 0) {
      this.error.set('Selecione um produto e informe uma quantidade válida para comparar.');
      return;
    }
    this.http.get<{content: Row[]}>('/api/admin/supplier-offers/compare', {
      params: { productId: this.compareProductId, quantity: decimal(this.compareQuantity) },
    }).subscribe({ next: r => this.comparisons.set(r.content), error: e => this.error.set(detail(e)) });
  }
  newOffer() {
    this.offerEditing = null;
    this.offerOpen = true;
    this.offerForm = { supplierId: this.supplierId || '', productId: this.productId || '',
      brand: '', supplierProductCode: '', unitCost: '', minimumOrderQuantity: '1',
      quotedAt: today(), validUntil: '', deliveryDays: '', freightCost: '0', notes: '', active: true };
    this.offerTiers = [];
  }
  editOffer(offer: Row) {
    this.offerEditing = offer.id;
    this.offerOpen = true;
    this.offerForm = {
      supplierId: String(offer['supplierId'] ?? ''), productId: String(offer['productId'] ?? ''),
      brand: String(offer['brand'] ?? ''), supplierProductCode: String(offer['supplierProductCode'] ?? ''),
      unitCost: String(offer['unitCost'] ?? ''), minimumOrderQuantity: String(offer['minimumOrderQuantity'] ?? '1'),
      quotedAt: String(offer['quotedAt'] ?? today()), validUntil: String(offer['validUntil'] ?? ''),
      deliveryDays: String(offer['deliveryDays'] ?? ''), freightCost: String(offer['freightCost'] ?? '0'),
      notes: String(offer['notes'] ?? ''), active: Boolean(offer['active']),
    };
    this.offerTiers = ((offer['tiers'] as Row[]) ?? []).map(t => ({
      minimumQuantity: String(t['minimumQuantity']), unitCost: String(t['unitCost']),
    }));
    if (!this.products().some(p => p.id === this.offerForm.productId)) {
      this.http.get<Row>('/api/admin/products/' + encodeURIComponent(this.offerForm.productId))
        .subscribe({ next: p => this.products.update(rows => [...rows.filter(x => x.id !== p.id), p]),
          error: e => this.error.set(detail(e)) });
    }
    if (!this.suppliers().some(s => s.id === this.offerForm.supplierId)) {
      this.http.get<Row>('/api/admin/contacts/SUPPLIER/' + encodeURIComponent(this.offerForm.supplierId))
        .subscribe({ next: s => this.suppliers.update(rows => [...rows.filter(x => x.id !== s.id), s]),
          error: e => this.error.set(detail(e)) });
    }
  }
  saveOffer() {
    const body = {
      ...this.offerForm,
      unitCost: decimal(this.offerForm.unitCost),
      minimumOrderQuantity: decimal(this.offerForm.minimumOrderQuantity),
      quotedAt: this.offerForm.quotedAt || null,
      validUntil: this.offerForm.validUntil || null,
      deliveryDays: this.offerForm.deliveryDays ? Number(this.offerForm.deliveryDays) : null,
      freightCost: decimal(this.offerForm.freightCost),
      tiers: this.offerTiers.map(t => ({ minimumQuantity: decimal(t.minimumQuantity), unitCost: decimal(t.unitCost) })),
    };
    this.action.submit('/api/admin/supplier-offers' + (this.offerEditing ? '/' + this.offerEditing : ''),
      body, () => { this.offerEditing = null; this.offerOpen = false; this.loadOffers(); this.compareOffers(); },
      this.offerEditing ? 'PUT' : 'POST');
  }
  deactivateOffer(offer: Row) {
    if (window.confirm('Desativar esta cotação? O histórico será preservado.'))
      this.action.submit('/api/admin/supplier-offers/' + offer.id + '/deactivate', {},
        () => this.loadOffers());
  }
  add() {
    const p = this.products().find((p) => p.id === this.productId);
    if (!p || !/^\d+([.,]\d{1,4})?$/.test(this.amount) || !/^\d+([.,]\d{1,2})?$/.test(this.cost)) {
      this.error.set('Informe produto, quantidade e custo válidos.');
      return;
    }
    if (this.lines.some((l) => l.productId === p.id)) {
      this.error.set('Produto já incluído. Ajuste a quantidade na linha.');
      return;
    }
    this.lines.push({
      productId: p.id,
      name: p['name'],
      quantity: this.amount,
      unitCost: this.cost,
    });
    this.error.set('');
  }
  save() {
    this.action.submit(
      '/api/admin/purchases',
      {
        supplierId: this.supplierId,
        items: this.lines.map((l) => ({
          productId: l.productId,
          quantity: decimal(l.quantity),
          unitCost: decimal(l.unitCost),
        })),
        dueDate: this.dueDate,
        note: this.note,
      },
      (r) => {
        this.open = false;
        this.lines = [];
        this.show(r.id);
        this.load();
      },
    );
  }
  show(id: string) {
    this.http.get<Row>('/api/admin/purchases/' + id).subscribe({
      next: (r) => {
        this.selected.set(r);
        this.receiveLines = r['items'].map((i: any) => ({
          itemId: i.id,
          name: i.name,
          unit: i.unitCode,
          ordered: i.quantity,
          received: i.received,
          quantity: '',
        }));
        this.receiptNote = '';
        this.cancelReason = '';
      },
      error: (e) => this.error.set(detail(e)),
    });
  }
  receive() {
    const order = this.selected();
    if (!order) return;
    const items = this.receiveLines
      .filter((i) => i.quantity.trim() !== '' && Number(decimal(i.quantity)) > 0)
      .map((i) => ({ itemId: i.itemId, quantity: decimal(i.quantity) }));
    this.action.submit(
      '/api/admin/purchases/' + order.id + '/receive',
      { items, note: this.receiptNote },
      (r) => {
        this.show(r.id);
        this.load();
      },
    );
  }
  returnReceipt(id: string) {
    const order = this.selected();
    if (
      order &&
      window.confirm(
        'Conferiu a devolução física e eventual reembolso do fornecedor? Confirmar devolução integral deste recebimento?',
      )
    )
      this.action.submit(
        '/api/admin/purchases/' + order.id + '/receipts/' + id + '/return',
        { reason: this.cancelReason },
        (r) => {
          this.show(r.id);
          this.load();
        },
      );
  }
  cancel() {
    const order = this.selected();
    if (order && window.confirm('Cancelar este pedido sem recebimentos?'))
      this.action.submit(
        '/api/admin/purchases/' + order.id + '/cancel',
        { reason: this.cancelReason },
        (r) => {
          this.show(r.id);
          this.load();
        },
      );
  }
}
