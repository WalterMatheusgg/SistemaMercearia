import { Routes } from '@angular/router';
import { adminGuard } from './auth/admin.guard';

export const routes: Routes = [
	{ path: 'login', loadComponent: () => import('./auth/login.component').then((module) => module.LoginComponent) },
	{
		path: 'admin',
		canActivate: [adminGuard],
		loadComponent: () => import('./admin/admin-layout.component').then((module) => module.AdminLayoutComponent),
		children: [
			{ path: '', pathMatch: 'full', redirectTo: 'products' },
			{ path: 'products', loadComponent: () => import('./products/product-list.component').then((module) => module.ProductListComponent) },
			{ path: 'inventory', loadComponent: () => import('./inventory/inventory.component').then((module) => module.InventoryComponent) },
		],
	},
	{ path: '', pathMatch: 'full', redirectTo: 'admin/products' },
	{ path: '**', redirectTo: 'admin/products' },
];
