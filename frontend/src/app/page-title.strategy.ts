import { Injectable, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';

export const APP_NAME = 'Loja';

export function pageTitle(label?: string): string {
  return label ? label + ' | ' + APP_NAME : APP_NAME;
}

/** Define o título da aba a partir do módulo ativo, sem dados do usuário ou do cliente. */
@Injectable()
export class LojaTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.title.setTitle(pageTitle(this.buildTitle(snapshot)));
  }
}
