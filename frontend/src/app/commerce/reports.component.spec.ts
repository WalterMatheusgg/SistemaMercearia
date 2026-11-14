import { describe, expect, it } from 'vitest';
import { beginningOfLastDays, validReportRange } from './reports.component';

describe('filtros do Excel gerencial', () => {
  it('inclui a data de hoje nas janelas de 5, 7 e 30 dias', () => {
    expect(beginningOfLastDays('2026-11-13', 5)).toBe('2026-11-09');
    expect(beginningOfLastDays('2026-11-13', 7)).toBe('2026-11-07');
    expect(beginningOfLastDays('2026-11-13', 30)).toBe('2026-10-15');
  });
  it('respeita início, fim e datas reais de calendário', () => {
    expect(validReportRange('2026-11-01','2026-11-13')).toBe(true);
    expect(validReportRange('2026-02-30','2026-03-02')).toBe(false);
    expect(validReportRange('2026-11-13','2026-11-01')).toBe(false);
    expect(validReportRange('2023-01-01','2026-11-13')).toBe(false);
  });
});
