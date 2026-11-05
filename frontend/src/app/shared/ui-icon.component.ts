import { Component, Input } from '@angular/core';

const paths: Record<string, string[]> = {
  package: ['M3 7l9-4 9 4v10l-9 4-9-4z','m3 7 9 4 9-4','M12 11v10'],
  boxes: ['M4 4h16v16H4z','M4 10h16','M10 10v10'],
  vendors: ['M3 21V7l9-4 9 4v14','M3 11h18','M8 21v-5h8v5'],
  cart: ['M4 4h2l2 11h11l2-8H7','M10 20h.01M19 20h.01'],
  coins: ['M3 6h18v13H3z','M3 10h18','M17 15h2'],
  graph: ['M3 20h18','M5 15v-4','M11 15V6','M17 15V9'],
  file: ['M6 3h10l4 4v14H6z','M16 3v5h4','M9 12h8','M9 16h8'],
  people: ['M9 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8','M3 21v-3a6 6 0 0 1 12 0v3','M17 5a3 3 0 0 1 0 6'],
  arrow: ['M5 12h14','m13 6 6 6-6 6'],
  wand: ['m4 20 13-13','m15 5 4 4','M3 4h4','M5 2v4','M19 16v6','M16 19h6'],
  search: ['M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16','m17 17 5 5'],
  clock: ['M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18','M12 7v5l3 2'],
  alert: ['M12 3 2 21h20L12 3z','M12 9v5','M12 18h.01'],
  plus: ['M12 5v14','M5 12h14'],
  filter: ['M4 5h16','M7 12h10','M10 19h4'],
  refresh: ['M3 12a9 9 0 0 1 16-5','M19 3v4h-4','M21 12a9 9 0 0 1-16 5','M5 21v-4h4'],
  spark: ['M12 2l2.5 7.5L22 12l-7.5 2.5L12 22l-2.5-7.5L2 12l7.5-2.5z'],
};
@Component({
  selector: 'app-ui-icon',
  standalone: true,
  template: `<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false" width="22" height="22"
     fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
      @for (d of (paths[name] ?? paths['package']); track $index) { <path [attr.d]="d" /> }
    </svg>`,
})
export class UiIconComponent {
  @Input() name = 'package';
  readonly paths = paths;
}
