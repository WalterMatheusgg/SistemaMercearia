import { Component, inject } from '@angular/core';
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
  has(roles: string[]): boolean {
    return this.auth.currentUser()?.roles.some((role) => roles.includes(role)) ?? false;
  }
  readonly links = [
    { path: '/admin/sales', icon: '▤', title: 'Vendas e pedidos', description: 'Orçamentos, vendas e pedidos em um só lugar.', roles: ['OWNER', 'MANAGER', 'SELLER', 'CASHIER'] },
    { path: '/admin/cash', icon: '▣', title: 'Caixa', description: 'Abertura, recebimentos e fechamento.', roles: ['OWNER', 'MANAGER', 'SELLER', 'CASHIER'] },
    { path: '/admin/inventory', icon: '▥', title: 'Estoque', description: 'Saldos, mínimos e movimentações auditadas.', roles: ['OWNER', 'MANAGER', 'SELLER', 'CASHIER', 'STOCKKEEPER'] },
    { path: '/admin/products', icon: '▦', title: 'Produtos', description: 'Organize o catálogo e os preços da loja.', roles: ['OWNER', 'MANAGER', 'STOCKKEEPER'] },
    { path: '/admin/purchases', icon: '⇄', title: 'Compras', description: 'Pedidos ao fornecedor e recebimentos.', roles: ['OWNER', 'MANAGER', 'STOCKKEEPER'] },
    { path: '/admin/finance', icon: '◫', title: 'Financeiro', description: 'Contas a pagar, a receber e liquidações.', roles: ['OWNER', 'MANAGER', 'FINANCE'] },
    { path: '/admin/reports', icon: '▧', title: 'Relatórios', description: 'Acompanhe o resultado das operações.', roles: ['OWNER', 'MANAGER', 'FINANCE'] },
    { path: '/admin/customers', icon: '◎', title: 'Clientes', description: 'Consulte e organize os contatos.', roles: ['OWNER', 'MANAGER', 'SELLER', 'CASHIER', 'FINANCE'] },
  ];
}
