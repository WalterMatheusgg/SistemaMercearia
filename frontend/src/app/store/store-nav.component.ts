import { Component, inject } from '@angular/core';
import { RouterLink, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { CartService } from './cart.service';
@Component({
  selector: 'app-store-nav',
  standalone: true,
  imports: [RouterLink],
  template: `<nav aria-label="Navegação da loja">
    <a class="brand" routerLink="/store">FERRAGENS</a>
    <div>
      <a routerLink="/store">Produtos</a
      ><a routerLink="/cart">Carrinho ({{ cart.items().length }})</a>
      @if (auth.currentUser(); as user) {
        <a [routerLink]="user.roles.includes('CUSTOMER') ? '/account' : '/admin'">{{
          user.roles.includes('CUSTOMER') ? 'Minha conta' : 'Gestão'
        }}</a>
      } @else {
        <a routerLink="/login">Entrar</a><a routerLink="/register">Criar conta</a>
      }
    </div>
  </nav>`,
  styles: [
    `
      nav {
        display: flex;
        justify-content: space-between;
        gap: 16px;
        align-items: center;
        padding: 20px 5vw;
        background: #123b5d;
        color: white;
        flex-wrap: wrap;
      }
      a {
        color: white;
        text-decoration: none;
      }
      a:hover {
        text-decoration: underline;
      }
      .brand {
        letter-spacing: 0.16em;
        font-weight: 800;
      }
      nav div {
        display: flex;
        gap: 20px;
        flex-wrap: wrap;
        font-size: 14px;
      }
      a:focus-visible {
        outline: 3px solid #e4a52c;
      }
    `,
  ],
})
export class StoreNavComponent {
  readonly auth = inject(AuthService);
  readonly cart = inject(CartService);
  constructor() {
    this.auth.loadCurrentUser().subscribe();
  }
}
