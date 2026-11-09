import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Dados de leitura; a consulta não movimenta estoque nem registra venda. */
export interface BarcodeItem {
  productId: string;
  barcode: string;
  name: string;
  unit: string;
  price: string;
  physical: string;
  available: string;
  minimumQuantity: string;
  quantityScale: number;
  quantityStep: string;
  revision: number;
  active: boolean;
}

@Injectable({ providedIn: 'root' })
export class BarcodeApi {
  private readonly http = inject(HttpClient);

  lookup(code: string, locationId = ''): Observable<BarcodeItem> {
    let params = new HttpParams().set('code', code.trim());
    if (locationId) params = params.set('locationId', locationId);
    return this.http.get<BarcodeItem>('/api/admin/barcodes/lookup', { params });
  }

  /** Até 10 sugestões; pesquisa por nome, código completo ou final da etiqueta. */
  search(query: string, locationId = ''): Observable<BarcodeItem[]> {
    let params = new HttpParams().set('query', query.trim());
    if (locationId) params = params.set('locationId', locationId);
    return this.http.get<BarcodeItem[]>('/api/admin/barcodes/search', { params });
  }

  product(id: string, locationId = ''): Observable<BarcodeItem> {
    let params = new HttpParams();
    if (locationId) params = params.set('locationId', locationId);
    return this.http.get<BarcodeItem>(`/api/admin/barcodes/product/${id}`, { params });
  }
}
