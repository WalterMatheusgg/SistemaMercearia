import { Routes } from '@angular/router';
import { adminGuard, customerGuard, roleGuard } from './auth/admin.guard';
import { pendingActionGuard } from './commerce/ui';
const management = roleGuard('OWNER', 'MANAGER');
const selling = roleGuard('OWNER', 'MANAGER', 'SELLER', 'CASHIER');
const finance = roleGuard('OWNER', 'MANAGER', 'FINANCE');
const stock = roleGuard('OWNER', 'MANAGER', 'STOCKKEEPER');
export const routes: Routes = [
  {
    path: 'store',
    loadComponent: () => import('./store/catalog.component').then((m) => m.CatalogComponent),
  },
  {
    path: 'cart',
    canDeactivate: [pendingActionGuard],
    loadComponent: () => import('./store/cart.component').then((m) => m.CartComponent),
  },
  {
    path: 'login',
    loadComponent: () => import('./auth/login.component').then((m) => m.LoginComponent),
  },
  ...(['register', 'forgot-password', 'reset-password', 'verify-email'] as const).map(
    (path, i) => ({
      path,
      data: { mode: ['register', 'forgot', 'reset', 'verify'][i] },
      canDeactivate: [pendingActionGuard],
      loadComponent: () =>
        import('./auth/account-forms.component').then((m) => m.AccountFormsComponent),
    }),
  ),
  {
    path: 'account',
    canActivate: [customerGuard],
    canDeactivate: [pendingActionGuard],
    loadComponent: () =>
      import('./store/account.component').then((m) => m.CustomerAccountComponent),
  },
  {
    path: 'admin',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./admin/admin-layout.component').then((m) => m.AdminLayoutComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'home' },
      {
        path: 'home',
        loadComponent: () => import('./commerce/home.component').then((m) => m.HomeComponent),
      },
      {
        path: 'products',
        canActivate: [stock],
        loadComponent: () =>
          import('./products/product-list.component').then((m) => m.ProductListComponent),
      },
      {
        path: 'inventory',
        canActivate: [roleGuard('OWNER', 'MANAGER', 'STOCKKEEPER', 'SELLER', 'CASHIER')],
        canDeactivate: [pendingActionGuard],
        loadComponent: () =>
          import('./inventory/inventory.component').then((m) => m.InventoryComponent),
      },
      {
        path: 'customers',
        canActivate: [roleGuard('OWNER', 'MANAGER', 'SELLER', 'CASHIER', 'FINANCE')],
        data: { kind: 'CUSTOMER' },
        canDeactivate: [pendingActionGuard],
        loadComponent: () =>
          import('./commerce/contacts.component').then((m) => m.ContactsComponent),
      },
      {
        path: 'suppliers',
        canActivate: [roleGuard('OWNER', 'MANAGER', 'STOCKKEEPER', 'FINANCE')],
        data: { kind: 'SUPPLIER' },
        canDeactivate: [pendingActionGuard],
        loadComponent: () =>
          import('./commerce/contacts.component').then((m) => m.ContactsComponent),
      },
      {
        path: 'sales',
        canActivate: [selling],
        canDeactivate: [pendingActionGuard],
        loadComponent: () => import('./commerce/sales.component').then((m) => m.SalesComponent),
      },
      {
        path: 'cash',
        canActivate: [selling],
        canDeactivate: [pendingActionGuard],
        loadComponent: () => import('./commerce/cash.component').then((m) => m.CashComponent),
      },
      {
        path: 'purchases',
        canActivate: [stock],
        canDeactivate: [pendingActionGuard],
        loadComponent: () =>
          import('./commerce/purchases.component').then((m) => m.PurchasesComponent),
      },
      {
        path: 'finance',
        canActivate: [finance],
        canDeactivate: [pendingActionGuard],
        loadComponent: () => import('./commerce/finance.component').then((m) => m.FinanceComponent),
      },
      {
        path: 'reports',
        canActivate: [finance],
        loadComponent: () => import('./commerce/reports.component').then((m) => m.ReportsComponent),
      },
      {
        path: 'users',
        canActivate: [management],
        canDeactivate: [pendingActionGuard],
        loadComponent: () => import('./commerce/users.component').then((m) => m.UsersComponent),
      },
      {
        path: 'notifications',
        canActivate: [stock],
        canDeactivate: [pendingActionGuard],
        loadComponent: () =>
          import('./commerce/notifications.component').then((m) => m.NotificationsComponent),
      },
      {
        path: 'settings',
        canActivate: [management],
        canDeactivate: [pendingActionGuard],
        loadComponent: () =>
          import('./commerce/settings.component').then((m) => m.SettingsComponent),
      },
    ],
  },
  { path: '', pathMatch: 'full', redirectTo: 'store' },
  { path: '**', redirectTo: 'store' },
];
