import { Component, input } from '@angular/core';
import { createAction } from './ui';
@Component({
  selector: 'app-action-status',
  standalone: true,
  template: `@if (action().busy() && !action().message()) {
      <p class="note" role="status" aria-live="polite">
        Processando solicitação. Não feche esta página enquanto a confirmação estiver pendente.
      </p>
    }
    @if (action().error()) {
      <p class="note error" role="alert">{{ action().error() }}</p>
    }
    @if (action().uncertain()) {
      <button type="button" (click)="action().retry()" [disabled]="action().busy()">
        Confirmar a mesma operação
      </button>
    }
    @if (action().message()) {
      <p class="note success" role="status">{{ action().message() }}</p>
    }`,
  styleUrl: './ui.scss',
})
export class ActionStatusComponent {
  readonly action = input.required<ReturnType<typeof createAction>>();
}
