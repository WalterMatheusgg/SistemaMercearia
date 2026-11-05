import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { A11yModule } from '@angular/cdk/a11y';
import { AuthService } from '../auth/auth.service';
import { UiIconComponent } from '../shared/ui-icon.component';

@Component({
  selector: 'app-quick-nav',
  standalone: true,
  imports: [RouterLink, A11yModule, UiIconComponent],
  templateUrl: './quick-nav.component.html',
  styleUrl: './quick-nav.component.scss',
})
export class QuickNavComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly open = signal(false);
  readonly query = signal('');
  readonly options = [
    { name: 'Painel', icon:'graph', path:'/admin/home', tags:'inicio central resumo', roles:[] },
    { name: 'Estoque', icon:'package', path:'/admin/inventory', tags:'saldos entradas saídas movimentações', roles:['OWNER','MANAGER','STOCKKEEPER','SELLER','CASHIER'] },
    { name: 'Produtos', icon:'boxes', path:'/admin/products', tags:'cadastro produto material', roles:['OWNER','MANAGER','STOCKKEEPER'] },
    { name: 'Fornecedores', icon:'vendors', path:'/admin/suppliers', tags:'compra fornecedor', roles:['OWNER','MANAGER','STOCKKEEPER','FINANCE'] },
    { name: 'Compras e recebimentos', icon:'package', path:'/admin/purchases', tags:'pedido fornecedor entrada', roles:['OWNER','MANAGER','STOCKKEEPER'] },
    { name: 'Vendas', icon:'cart', path:'/admin/sales', tags:'atendimento orçamento pedido', roles:['OWNER','MANAGER','SELLER','CASHIER'] },
    { name: 'Dinheiro', icon:'coins', path:'/admin/cash', tags:'movimentações entrada saída físico', roles:['OWNER','MANAGER','SELLER','CASHIER'] },
    { name: 'Clientes', icon:'people', path:'/admin/customers', tags:'contato', roles:['OWNER','MANAGER','FINANCE','SELLER','CASHIER'] },
    { name: 'Financeiro', icon:'graph', path:'/admin/finance', tags:'contas pagar receber', roles:['OWNER','MANAGER','FINANCE'] },
    { name: 'Relatórios', icon:'file', path:'/admin/reports', tags:'indicadores vendas', roles:['OWNER','MANAGER','FINANCE'] },
    { name: 'Assistente', icon:'wand', path:'/admin/assistant', tags:'ia chat importar lote', roles:[] },
    { name: 'Equipe', icon:'people', path:'/admin/users', tags:'usuarios acessos', roles:['OWNER','MANAGER'] },
    { name: 'Configurações', icon:'filter', path:'/admin/settings', tags:'loja auditoria', roles:['OWNER','MANAGER'] },
  ];
  readonly filtered = computed(() => {
    const roles = this.auth.currentUser()?.roles ?? [];
    const term = this.query().trim().toLocaleLowerCase('pt-BR');
    return this.options.filter(x => (!x.roles.length || x.roles.some(role => roles.includes(role)))
      && (x.name.toLocaleLowerCase('pt-BR').includes(term) || x.tags.includes(term)));
  });

  @HostListener('document:keydown', ['$event'])
  keyboard(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.toggle();
    } else if (event.key === 'Escape' && this.open()) {
      this.toggle(false);
    }
  }
  toggle(force?: boolean): void {
    this.open.set(force ?? !this.open());
    if (this.open()) this.query.set('');
  }
  search(text: string): void { this.query.set(text); }
  navigate(path: string): void {
    this.toggle(false);
    void this.router.navigateByUrl(path);
  }
}
