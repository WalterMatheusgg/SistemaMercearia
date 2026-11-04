import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../auth/auth.service';
@Component({
  standalone: true,
  imports: [RouterLink],
  template: `<section class="page">
    <header>
      <div>
        <p class="eyebrow">ÁREA INTERNA</p>
        <h1>Operação da loja</h1>
        <small>Olá, {{ auth.currentUser()?.username }}. Escolha o que precisa fazer.</small>
      </div>
    </header>
    <div class="cards">
      @for (link of links; track link.path) {
        @if (has(link.roles)) {
          <a class="card" [routerLink]="link.path"
            ><strong>{{ link.title }}</strong>
            <p>{{ link.description }}</p></a
          >
        }
      }
    </div>
    <section class="panel">
      <h2>Como os fluxos se conectam</h2>
      <p>
        Compras aumentam o estoque quando recebidas. Vendas de balcão baixam o físico e registram
        recebimentos ou valores a receber. Pedidos online reservam os itens até a confirmação da
        loja. E-mails seguem suas preferências.
      </p>
      <p>
        Antes da primeira venda, confira produtos, saldo inicial, usuários, configurações e abertura
        do caixa. Pagamentos são conferidos manualmente no banco ou na adquirente.
      </p>
      <a class="button" routerLink="/store">Abrir loja virtual</a>
    </section>
  </section>`,
  styleUrl: './ui.scss',
})
export class HomeComponent {
  readonly auth = inject(AuthService);
  has(roles: string[]) {
    return this.auth.currentUser()?.roles.some((r) => roles.includes(r));
  }
  readonly links = [
    {
      path: '/admin/sales',
      title: 'Vendas e pedidos',
      description: 'Atender, orçar e acompanhar compras.',
      roles: ['OWNER', 'MANAGER', 'SELLER', 'CASHIER'],
    },
    {
      path: '/admin/cash',
      title: 'Caixa',
      description: 'Abrir, conferir e fechar a gaveta.',
      roles: ['OWNER', 'MANAGER', 'SELLER', 'CASHIER'],
    },
    {
      path: '/admin/inventory',
      title: 'Estoque',
      description: 'Consultar saldos e movimentações.',
      roles: ['OWNER', 'MANAGER', 'SELLER', 'CASHIER', 'STOCKKEEPER'],
    },
    {
      path: '/admin/purchases',
      title: 'Compras',
      description: 'Pedir e receber materiais.',
      roles: ['OWNER', 'MANAGER', 'STOCKKEEPER'],
    },
    {
      path: '/admin/finance',
      title: 'Financeiro',
      description: 'Pagar, receber e conferir lançamentos.',
      roles: ['OWNER', 'MANAGER', 'FINANCE'],
    },
    {
      path: '/admin/reports',
      title: 'Relatórios',
      description: 'Vendas, produtos e contas pendentes.',
      roles: ['OWNER', 'MANAGER', 'FINANCE'],
    },
  ];
}
