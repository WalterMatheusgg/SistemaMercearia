import { Routes } from '@angular/router';
import { adminGuard, roleGuard } from './auth/admin.guard';
import { pendingActionGuard } from './commerce/ui';
const management = roleGuard('OWNER', 'MANAGER');
const selling = roleGuard('OWNER', 'MANAGER', 'SELLER', 'CASHIER');
const finance = roleGuard('OWNER', 'MANAGER', 'FINANCE');
const stock = roleGuard('OWNER', 'MANAGER', 'STOCKKEEPER');
export const routes: Routes = [
  {
    path: 'indisponivel',
    title: 'Conexão indisponível',
    loadComponent: () => import('./shared/service-unavailable.component')
      .then(m => m.ServiceUnavailableComponent),
  },
  {
    path: 'login',
    title: 'Entrar',
    loadComponent: () => import('./auth/login.component').then((m) => m.LoginComponent),
  },
  ...(['forgot-password', 'reset-password'] as const).map(
    (path, i) => ({
      path,
      data: { mode: ['forgot', 'reset'][i] },
      title: ['Recuperar senha', 'Redefinir senha'][i],
      canDeactivate: [pendingActionGuard],
      loadComponent: () =>
        import('./auth/account-forms.component').then((m) => m.AccountFormsComponent),
    }),
  ),
  {
    path: 'admin',
    title: 'Gestão',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./admin/admin-layout.component').then((m) => m.AdminLayoutComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'home' },
      {
        path: 'home',
        title: 'Painel',
        loadComponent: () => import('./commerce/home.component').then((m) => m.HomeComponent),
      },
      {
        path: 'assistant',
        title: 'Assistente',
        canActivate: [roleGuard('OWNER','MANAGER','STOCKKEEPER','SELLER','CASHIER','FINANCE')],
        loadComponent: () => import('./commerce/assistant.component').then(m => m.AssistantComponent),
      },
      {
        path: 'products',
        title: 'Produtos',
        canActivate: [stock],
        loadComponent: () =>
          import('./products/product-list.component').then((m) => m.ProductListComponent),
      },
      {
        path: 'inventory',
        title: 'Estoque',
        canActivate: [roleGuard('OWNER', 'MANAGER', 'STOCKKEEPER', 'SELLER', 'CASHIER')],
        canDeactivate: [pendingActionGuard],
        loadComponent: () =>
          import('./inventory/inventory.component').then((m) => m.InventoryComponent),
      },
      {
        path: 'customers',
        title: 'Clientes',
        canActivate: [roleGuard('OWNER', 'MANAGER', 'SELLER', 'CASHIER', 'FINANCE')],
        data: { kind: 'CUSTOMER' },
        canDeactivate: [pendingActionGuard],
        loadComponent: () =>
          import('./commerce/contacts.component').then((m) => m.ContactsComponent),
      },
      {
        path: 'suppliers',
        title: 'Fornecedores',
        canActivate: [roleGuard('OWNER', 'MANAGER', 'STOCKKEEPER', 'FINANCE')],
        data: { kind: 'SUPPLIER' },
        canDeactivate: [pendingActionGuard],
        loadComponent: () =>
          import('./commerce/contacts.component').then((m) => m.ContactsComponent),
      },
      {
        path: 'sales',
        title: 'Vendas e pedidos',
        canActivate: [selling],
        canDeactivate: [pendingActionGuard],
        loadComponent: () => import('./commerce/sales.component').then((m) => m.SalesComponent),
      },
      {
        path: 'cash',
        title: 'Caixa',
        canActivate: [selling],
        canDeactivate: [pendingActionGuard],
        loadComponent: () => import('./commerce/cash.component').then((m) => m.CashComponent),
      },
      {
        path: 'purchases',
        title: 'Compras',
        canActivate: [stock],
        canDeactivate: [pendingActionGuard],
        loadComponent: () =>
          import('./commerce/purchases.component').then((m) => m.PurchasesComponent),
      },
      {
        path: 'finance',
        title: 'Financeiro',
        canActivate: [finance],
        canDeactivate: [pendingActionGuard],
        loadComponent: () => import('./commerce/finance.component').then((m) => m.FinanceComponent),
      },
      {
        path: 'reports',
        title: 'Relatórios',
        canActivate: [finance],
        loadComponent: () => import('./commerce/reports.component').then((m) => m.ReportsComponent),
      },
      {
        path: 'users',
        title: 'Usuários',
        canActivate: [management],
        canDeactivate: [pendingActionGuard],
        loadComponent: () => import('./commerce/users.component').then((m) => m.UsersComponent),
      },
      {
        path: 'notifications',
        title: 'Alertas por e-mail',
        canActivate: [stock],
        canDeactivate: [pendingActionGuard],
        loadComponent: () =>
          import('./commerce/notifications.component').then((m) => m.NotificationsComponent),
      },
      {
        path: 'settings',
        title: 'Loja e auditoria',
        canActivate: [management],
        canDeactivate: [pendingActionGuard],
        loadComponent: () =>
          import('./commerce/settings.component').then((m) => m.SettingsComponent),
      },
    ],
  },
  { path: '', pathMatch: 'full', redirectTo: 'admin/home' },
  { path: '**', redirectTo: 'admin/home' },
];
