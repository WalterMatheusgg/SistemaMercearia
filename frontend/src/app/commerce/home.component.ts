import { Component, inject, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { switchMap, of, forkJoin, catchError } from 'rxjs';
import { RouterLink } from '@angular/router';
import { AuthService } from '../auth/auth.service';

@Component({
  standalone: true,
  imports: [RouterLink],
  templateUrl: './home.component.html',
  styleUrls: ['./ui.scss', './home.component.scss'],
})
export class HomeComponent {
  readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  readonly searchTerm = signal('');
  readonly activeGroup = signal<'all' | 'estoque' | 'comercial' | 'financeiro'>('all');
  readonly motionPaused = signal(false);
  readonly updating = signal(false);
  readonly productsTotal = signal<number | null>(null);
  readonly lowStockTotal = signal<number | null>(null);
  readonly todaySales = signal<number | null>(null);
  readonly todayRevenue = signal<string | null>(null);
  readonly lastUpdated = signal('');
  readonly todayLabel = new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'America/Sao_Paulo' }).format(new Date());
  readonly filteredLinks = computed(() => {
    const search = this.searchTerm().toLocaleLowerCase('pt-BR');
    const group = this.activeGroup();
    return this.links.filter((link) => this.has(link.roles)
      && (group === 'all' || link.group === group)
      && (link.title.toLocaleLowerCase('pt-BR').includes(search)
        || link.description.toLocaleLowerCase('pt-BR').includes(search)));
  });

  constructor() { this.refreshDashboard(); }

  refreshDashboard(): void {
    this.updating.set(true);
    const canProducts = this.has(['OWNER','MANAGER','STOCKKEEPER']);
    const canInventory = this.has(['OWNER','MANAGER','STOCKKEEPER','SELLER','CASHIER']);
    const canReports = this.has(['OWNER','MANAGER','FINANCE']);
    const products$ = canProducts
      ? this.http.get<{ totalElements: number }>('/api/admin/products', { params: { active: 'true', size: 1 } }).pipe(catchError(() => of(null)))
      : of(null);
    const lowStock$ = canInventory
      ? this.http.get<{ id: string }[]>('/api/admin/inventory/locations').pipe(
          switchMap((locations) => locations.length
            ? this.http.get<{ totalElements: number }>('/api/admin/inventory/balances',
                { params: { locationId: locations[0].id, onlyLow: true, size: 1 } })
            : of(null)), catchError(() => of(null)))
      : of(null);
    const report$ = canReports
      ? this.http.get<{ sales: number; revenue: string }>('/api/admin/reports',
          { params: { start: this.todayLocal(), end: this.todayLocal() } }).pipe(catchError(() => of(null)))
      : of(null);
    forkJoin({ products: products$, lowStock: lowStock$, report: report$ }).subscribe({
      next: (result) => {
        this.productsTotal.set(result.products?.totalElements ?? null);
        this.lowStockTotal.set(result.lowStock?.totalElements ?? null);
        this.todaySales.set(result.report?.sales ?? null);
        this.todayRevenue.set(result.report?.revenue ?? null);
        this.lastUpdated.set(new Intl.DateTimeFormat('pt-BR', { timeStyle: 'short' }).format(new Date()));
        this.updating.set(false);
      },
      error: () => this.updating.set(false),
    });
  }

  private todayLocal(): string {
    const parts = new Intl.DateTimeFormat('en-US', { timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
    const part = (type: string) => parts.find((p) => p.type === type)!.value;
    return part('year') + '-' + part('month') + '-' + part('day');
  }

  formattedRevenue(): string {
    const value = this.todayRevenue();
    if (value === null) return '—';
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(Number(value));
  }

  filterModules(text: string): void { this.searchTerm.set(text); }
  chooseGroup(group: 'all' | 'estoque' | 'comercial' | 'financeiro'): void { this.activeGroup.set(group); }
  has(roles: string[]): boolean {
    return this.auth.currentUser()?.roles.some((role) => roles.includes(role)) ?? false;
  }
  readonly links = [
    { group: 'comercial', path: '/admin/sales', icon: '▤', title: 'Vendas e pedidos', description: 'Orçamentos, vendas e pedidos em um só lugar.', roles: ['OWNER', 'MANAGER', 'SELLER', 'CASHIER'] },
    { group: 'comercial', path: '/admin/cash', icon: '▣', title: 'Dinheiro', description: 'Entradas e retiradas de dinheiro físico.', roles: ['OWNER', 'MANAGER', 'SELLER', 'CASHIER'] },
    { group: 'estoque', path: '/admin/inventory', icon: '▥', title: 'Estoque', description: 'Saldos, mínimos e movimentações auditadas.', roles: ['OWNER', 'MANAGER', 'SELLER', 'CASHIER', 'STOCKKEEPER'] },
    { group: 'estoque', path: '/admin/products', icon: '▦', title: 'Produtos', description: 'Organize o catálogo e os preços da loja.', roles: ['OWNER', 'MANAGER', 'STOCKKEEPER'] },
    { group: 'estoque', path: '/admin/purchases', icon: '⇄', title: 'Compras', description: 'Pedidos ao fornecedor e recebimentos.', roles: ['OWNER', 'MANAGER', 'STOCKKEEPER'] },
    { group: 'financeiro', path: '/admin/finance', icon: '◫', title: 'Financeiro', description: 'Contas a pagar, a receber e liquidações.', roles: ['OWNER', 'MANAGER', 'FINANCE'] },
    { group: 'financeiro', path: '/admin/reports', icon: '▧', title: 'Relatórios', description: 'Acompanhe o resultado das operações.', roles: ['OWNER', 'MANAGER', 'FINANCE'] },
    { group: 'comercial', path: '/admin/customers', icon: '◎', title: 'Clientes', description: 'Consulte e organize os contatos.', roles: ['OWNER', 'MANAGER', 'SELLER', 'CASHIER', 'FINANCE'] },
  ];
}
