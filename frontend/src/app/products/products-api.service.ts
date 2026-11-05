import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface Product {
  id: string;
  code: string;
  name: string;
  description: string | null;
  categoryId: string;
  categoryName: string;
  baseUnitCode: string;
  baseUnitName: string;
  cost: string;
  price: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ProductInput {
  code?: string;
  name: string;
  description: string | null;
  categoryId: string | null;
  baseUnitCode: string;
  cost: string;
  price: string;
}

export interface PageResult<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface Lookup {
  code: string;
  name: string;
}

@Injectable({ providedIn: 'root' })
export class ProductsApi {
  constructor(private readonly http: HttpClient) {}

  list(page: number, size: number, query: string, active: boolean | null = null): Observable<PageResult<Product>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (query.trim()) params = params.set('query', query.trim());
    if (active !== null) params = params.set('active', active);
    return this.http.get<PageResult<Product>>('/api/admin/products', { params });
  }

  categories(): Observable<Lookup[]> {
    return this.http.get<Lookup[]>('/api/admin/categories');
  }

  units(): Observable<Lookup[]> {
    return this.http.get<Lookup[]>('/api/admin/units');
  }


  /** Novo cadastro único: foto e quantidade inicial opcionais. */
  createComplete(product: ProductInput, quantity: string, photo?: File): Observable<Product> {
    const data = new FormData();
    data.append('data', new Blob([JSON.stringify({ product, initialQuantity: quantity || '0' })],
      { type: 'application/json' }), 'data.json');
    if (photo) data.append('photo', photo, photo.name);
    return this.http.post<Product>('/api/admin/products/complete', data);
  }

  updateComplete(id: string, product: ProductInput, photo?: File): Observable<Product> {
    const data = new FormData();
    data.append('data', new Blob([JSON.stringify(product)], { type: 'application/json' }), 'data.json');
    if (photo) data.append('photo', photo, photo.name);
    return this.http.put<Product>(`/api/admin/products/${id}/complete`, data);
  }

  create(product: ProductInput): Observable<Product> {
    return this.http.post<Product>('/api/admin/products', product);
  }

  update(id: string, product: ProductInput): Observable<Product> {
    return this.http.put<Product>(`/api/admin/products/${id}`, product);
  }

  reactivate(id: string): Observable<Product> {
    return this.http.post<Product>(`/api/admin/products/${id}/reactivate`, {});
  }

  deactivate(id: string): Observable<void> {
    return this.http.delete<void>(`/api/admin/products/${id}`);
  }
}