import { Injectable, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';

export const APP_NAME = 'ForjaHub';
export function pageTitle(label?: string): string {
  return label ? label + ' | ' + APP_NAME : APP_NAME + ' | Gestão e loja';
}

@Injectable()
export class ForjaTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.title.setTitle(pageTitle(this.buildTitle(snapshot)));
  }
}
