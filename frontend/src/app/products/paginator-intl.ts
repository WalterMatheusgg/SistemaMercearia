import { MatPaginatorIntl } from '@angular/material/paginator';

export function portuguesePaginator(): MatPaginatorIntl {
  const paginator = new MatPaginatorIntl();
  paginator.itemsPerPageLabel = 'Itens por página';
  paginator.nextPageLabel = 'Próxima página';
  paginator.previousPageLabel = 'Página anterior';
  paginator.firstPageLabel = 'Primeira página';
  paginator.lastPageLabel = 'Última página';
  paginator.getRangeLabel = (page, size, length) => length === 0 || size === 0
    ? `0 de ${length}` : `${page * size + 1}–${Math.min((page + 1) * size, length)} de ${length}`;
  return paginator;
}
