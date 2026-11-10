import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ConnectionHealth {
  readonly status = signal<'ok'|'retrying'|'unavailable'>('ok');
  retrying(): void { if (this.status() !== 'unavailable') this.status.set('retrying'); }
  unavailable(): void { this.status.set('unavailable'); }
  healthy(): void { this.status.set('ok'); }
}
