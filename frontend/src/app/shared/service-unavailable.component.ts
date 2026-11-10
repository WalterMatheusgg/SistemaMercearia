import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-service-unavailable',
  standalone: true,
  imports: [RouterLink],
  template: `
    <main class="recovery">
      <section class="panel" role="alert">
        <p class="eyebrow">CONEXÃO TEMPORARIAMENTE INDISPONÍVEL</p>
        <h1>O servidor da Loja não respondeu</h1>
        <p>Ele pode estar iniciando ou o banco estar ocupado. Isso não significa que
           seus produtos ou seus registros foram apagados.</p>
        <p><strong>Se estava concluindo uma venda, confira o histórico antes de repetir a operação.</strong></p>
        <div class="actions">
          <a routerLink="/admin/home">Tentar novamente</a>
          <a routerLink="/login" class="secondary">Voltar ao login</a>
        </div>
      </section>
    </main>
  `,
  styles: [`
    :host{display:block;min-height:100vh;background:#f4faf5}
    .recovery{min-height:100vh;display:grid;place-items:center;padding:20px}
    .panel{width:min(100%,590px);padding:clamp(25px,5vw,44px);background:#fff;
      border:1px solid #d8e6dc;border-radius:19px;box-shadow:0 20px 50px #123b2b16}
    .eyebrow{font-size:11px;letter-spacing:.13em;font-weight:850;color:#34765c}
    h1{font-size:clamp(27px,4vw,36px);line-height:1.2;color:#143b2c;margin:12px 0 22px}
    p{color:#53665c;line-height:1.7}strong{color:#274b39}
    .actions{display:flex;gap:12px;flex-wrap:wrap;margin-top:23px}
    a{min-height:44px;display:grid;place-items:center;padding:12px 18px;border-radius:9px;
      background:#1f6c4d;color:#fff;font-weight:750;text-decoration:none}
    a.secondary{background:#eef6f0;color:#215f44}
  `],
})
export class ServiceUnavailableComponent {}
