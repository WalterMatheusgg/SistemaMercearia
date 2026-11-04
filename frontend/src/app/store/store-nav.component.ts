import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { CartService } from './cart.service';

@Component({
  selector: 'app-store-nav',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './store-nav.component.html',
  styleUrl: './store-nav.component.scss',
})
export class StoreNavComponent {
  readonly auth = inject(AuthService);
  readonly cart = inject(CartService);
  constructor() {
    this.auth.loadCurrentUser().subscribe();
  }
}
