import { DestroyRef, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, switchMap } from 'rxjs';
import { CanDeactivateFn } from '@angular/router';

export interface Row {
  id: string;
  [key: string]: any;
}
export interface Page {
  content: Row[];
  totalElements: number;
  page: number;
  size: number;
}
export const labels: Record<string, string> = {
  POS: 'Balcão',
  ONLINE: 'Online',
  QUOTE: 'Orçamento',
  PENDING_PAYMENT: 'Aguardando pagamento',
  COMPLETED: 'Concluído',
  PAID: 'Pago',
  READY: 'Pronto',
  DISPATCHED: 'Em entrega',
  CANCELLED: 'Cancelado',
  EXPIRED: 'Reserva expirada',
  OPEN: 'Em aberto',
  PARTIAL: 'Parcial',
  RECEIVED: 'Recebido',
  PAYABLE: 'A pagar',
  RECEIVABLE: 'A receber',
  CASH: 'Dinheiro',
  PIX: 'Pix conferido',
  DEBIT_CARD: 'Cartão de débito',
  CREDIT_CARD: 'Cartão de crédito',
  TRANSFER: 'Transferência',
  PICKUP: 'Retirada',
  DELIVERY: 'Entrega',
  PENDING: 'Pendente',
  PROCESSING: 'Enviando',
  SENT: 'Enviado',
  FAILED: 'Falhou',
  SALE_COMPLETED: 'Venda concluída',
  STOCK_LOW: 'Estoque baixo',
  DAILY_SUMMARY: 'Resumo diário',
  TRANSACTIONAL: 'Conta e acesso',
  SUPPLY: 'Suprimento',
  WITHDRAWAL: 'Sangria',
  PAYMENT: 'Pagamento',
  REFUND: 'Estorno',
  OWNER: 'Dono',
  MANAGER: 'Gerente',
  SELLER: 'Vendedor',
  CASHIER: 'Caixa',
  STOCKKEEPER: 'Estoquista',
  FINANCE: 'Financeiro',
  CUSTOMER: 'Cliente',
};
export const label = (value: string): string => labels[value] ?? value;
export function money(value: string | number | null | undefined): string {
  const [whole, fraction = ''] = String(value ?? '0').split('.');
  return 'R$ ' + whole.replace(/\B(?=(\d{3})+(?!\d))/g, '.') + ',' + (fraction + '00').slice(0, 2);
}
export function quantity(value: string | number | null | undefined): string {
  return String(value ?? '0')
    .replace(/(\.\d*?)0+$/, '$1')
    .replace(/\.$/, '')
    .replace('.', ',');
}
export function date(value: string | null | undefined): string {
  if (!value) return '—';
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return value.split('-').reverse().join('/');
  return new Intl.DateTimeFormat('pt-BR', {
    timeZone: 'America/Sao_Paulo',
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value));
}
export function today(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date());
}
export function decimal(value: string): string {
  return value.trim().replace(',', '.');
}
export function detail(error: unknown): string {
  return (
    (error as HttpErrorResponse)?.error?.detail ??
    'Não foi possível concluir. Confira a conexão e tente novamente.'
  );
}

export function createAction() {
  const http = inject(HttpClient),
    destroy = inject(DestroyRef);
  const busy = signal(false),
    uncertain = signal(false),
    error = signal(''),
    message = signal('');
  let pending: {
    url: string;
    body: unknown;
    method: string;
    key: string;
    done: (value: any) => void;
  } | null = null;
  const beforeUnload = (event: BeforeUnloadEvent) => {
    if (uncertain() || busy()) {
      event.preventDefault();
      event.returnValue = '';
    }
  };
  window.addEventListener('beforeunload', beforeUnload);
  destroy.onDestroy(() => window.removeEventListener('beforeunload', beforeUnload));
  const submit = (url: string, body: unknown, done: (value: any) => void, method = 'POST') => {
    if (busy()) return;
    if (!pending)
      pending = { url, body: structuredClone(body), method, key: crypto.randomUUID(), done };
    busy.set(true);
    error.set('');
    message.set('');
    const job = pending;
    // A navigation into a different screen must not turn an indeterminate response into a second write.
    http
      .get('/api/auth/csrf')
      .pipe(
        switchMap(() =>
          http.request<any>(job.method, job.url, {
            body: job.body,
            headers: { 'Idempotency-Key': job.key },
          }),
        ),
        takeUntilDestroyed(destroy),
        finalize(() => busy.set(false)),
      )
      .subscribe({
        next: (value) => {
          pending = null;
          uncertain.set(false);
          message.set('Operação confirmada.');
          job.done(value);
        },
        error: (e: HttpErrorResponse) => {
          if (e.status === 0 || e.status >= 500) {
            uncertain.set(true);
            error.set(
              'Resultado ainda não confirmado. Use “Confirmar a mesma operação” para consultar sem duplicar.',
            );
          } else {
            pending = null;
            uncertain.set(false);
            error.set(detail(e));
          }
        },
      });
  };
  return {
    busy,
    uncertain,
    error,
    message,
    locked: () => busy() || uncertain(),
    submit,
    retry: () => {
      if (pending) submit(pending.url, pending.body, pending.done, pending.method);
    },
    canLeave: () =>
      !busy() &&
      (!uncertain() ||
        window.confirm(
          'O resultado ainda não foi confirmado. Confira o histórico antes de registrar outra operação. Deseja sair?',
        )),
  };
}
export const pendingActionGuard: CanDeactivateFn<{
  action?: ReturnType<typeof createAction>;
  saving?: () => boolean;
  uncertain?: () => boolean;
}> = (component) =>
  component.action
    ? component.action.canLeave()
    : !component.saving?.() &&
      (!component.uncertain?.() ||
        window.confirm(
          'Operação ainda não confirmada. Confira o histórico antes de registrar novamente. Sair?',
        ));
