import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export type MovementKind = 'INITIAL' | 'ENTRY' | 'EXIT' | 'LOSS' | 'COUNT' | 'MINIMUM' | 'REVERSAL';
export interface InventoryLocation { id: string; name: string; }
export interface InventoryCategory { id: string; name: string; }
export interface InventoryBalance {
  productId: string; locationId: string; code: string; name: string; unit: string;
  quantityScale: number; quantityStep: string; physical: string; reserved: string;
  unavailable: string; available: string; minimumQuantity: string; revision: number;
  status: 'EMPTY' | 'LOW' | 'NORMAL'; active: boolean;
}
export interface InventoryMovement {
  id: string; productId: string; locationId: string; code: string; productName: string; unit: string;
  kind: MovementKind; quantity: string; delta: string; balanceBefore: string; balanceAfter: string;
  minimumBefore: string; minimumAfter: string; reason: string; actorName: string;
  occurredAt: string; reversesMovementId: string | null; reversed: boolean;
}
export interface MovementCommand {
  productId: string; locationId: string; kind: MovementKind; quantity: string;
  reason: string; expectedRevision: number | null; reversesMovementId: string | null;
}
export interface InventoryPage<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number; }

@Injectable({ providedIn: 'root' })
export class InventoryApi {
  constructor(private readonly http: HttpClient) {}

  locations(): Observable<InventoryLocation[]> {
    return this.http.get<InventoryLocation[]>('/api/admin/inventory/locations');
  }

  categories(): Observable<InventoryCategory[]> {
    return this.http.get<InventoryCategory[]>('/api/admin/inventory/categories');
  }

  balances(locationId: string, query: string, onlyLow: boolean, includeInactive: boolean,
    page: number, categoryId = ''): Observable<InventoryPage<InventoryBalance>> {
    let params = new HttpParams().set('locationId', locationId).set('query', query)
      .set('onlyLow', onlyLow).set('includeInactive', includeInactive).set('page', page).set('size', 20);
    if (categoryId) params = params.set('categoryId', categoryId);
    return this.http.get<InventoryPage<InventoryBalance>>('/api/admin/inventory/balances', { params });
  }

  history(locationId: string, productId: string | null, kind: MovementKind | '', page: number): Observable<InventoryPage<InventoryMovement>> {
    let params = new HttpParams().set('locationId', locationId).set('page', page).set('size', 20);
    if (productId) params = params.set('productId', productId);
    if (kind) params = params.set('kind', kind);
    return this.http.get<InventoryPage<InventoryMovement>>('/api/admin/inventory/movements', { params });
  }

  execute(command: MovementCommand, key: string): Observable<InventoryMovement> {
    return this.http.post<InventoryMovement>('/api/admin/inventory/movements', command, {
      headers: new HttpHeaders().set('Idempotency-Key', key),
    });
  }
}
