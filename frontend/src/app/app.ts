import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { inject } from '@angular/core';
import { ConnectionHealth } from './shared/connection-health.service';

@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  template: `
    @if (health.status() !== 'ok') {
      <section class="connection-notice" role="status" aria-live="polite">
        <strong>{{ health.status() === 'retrying' ? 'Reconectando à Loja...' : 'Servidor temporariamente indisponível.' }}</strong>
        <span>{{ health.status() === 'retrying' ? 'Aguardando resposta para leitura de dados.' : 'Confira o histórico antes de repetir uma venda ou pagamento.' }}</span>
        @if (health.status() === 'unavailable') {
          <button type="button" (click)="reload()">Tentar novamente</button>
        }
      </section>
    }
    <router-outlet />
  `,
  styles: [`
    .connection-notice{position:relative;z-index:90;display:flex;flex-wrap:wrap;align-items:center;justify-content:center;
      gap:8px 14px;padding:12px 16px;background:#fff4e4;border-bottom:1px solid #e6cba6;color:#613f24;font-size:13px}
    .connection-notice strong{font-weight:850}.connection-notice span{line-height:1.5}
    .connection-notice button{min-height:44px;border:1px solid #987450;border-radius:9px;padding:10px 16px;
      background:#fff;color:#553a22;font:inherit;font-weight:750;cursor:pointer}
  `,
})
export class App {
  readonly health = inject(ConnectionHealth);
  reload(): void { window.location.reload(); }
}
