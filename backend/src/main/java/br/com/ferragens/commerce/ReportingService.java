package br.com.ferragens.commerce;

import br.com.ferragens.auth.CurrentActorService.Actor;
import br.com.ferragens.shared.BusinessRuleException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ReportingService {
    private final Db db;

    public ReportingService(Db db) {
        this.db = db;
    }

    public Map<String, Object> report(Actor actor, LocalDate start, LocalDate end) {
        if (start == null) start = Db.today().withDayOfMonth(1);
        if (end == null) end = Db.today();
        if (end.isBefore(start) || end.isAfter(start.plusYears(2)))
            throw new BusinessRuleException("Selecione um intervalo de até dois anos.");
        var from = start.atStartOfDay(Db.ZONE).toOffsetDateTime();
        var to = end.plusDays(1).atStartOfDay(Db.ZONE).toOffsetDateTime();
        var totals =
                db.one(
                        "SELECT count(*) AS sales,coalesce(sum(total),0) AS"
                            + " revenue,coalesce(sum(discount),0) AS"
                            + " discounts,coalesce(avg(total),0)::numeric(14,2) AS average_ticket"
                            + " FROM sale WHERE store_id=? AND confirmed_at>=? AND confirmed_at<?"
                            + " AND status NOT IN"
                            + " ('CANCELLED','EXPIRED','QUOTE','PENDING_PAYMENT')",
                        actor.storeId(),
                        from,
                        to);
        totals.put("start", start.toString());
        totals.put("end", end.toString());
        totals.put(
                "receivables",
                db.one(
                                "SELECT coalesce(sum(amount-paid),0) AS pending FROM"
                                    + " financial_account WHERE store_id=? AND"
                                    + " direction='RECEIVABLE' AND status IN ('OPEN','PARTIAL')",
                                actor.storeId())
                        .get("pending"));
        totals.put(
                "payables",
                db.one(
                                "SELECT coalesce(sum(amount-paid),0) AS pending FROM"
                                    + " financial_account WHERE store_id=? AND direction='PAYABLE'"
                                    + " AND status IN ('OPEN','PARTIAL')",
                                actor.storeId())
                        .get("pending"));
        totals.put(
                "payments",
                db.rows(
                        "SELECT p.method,coalesce(sum(CASE WHEN p.direction='IN' THEN p.amount ELSE"
                                + " -p.amount END),0) AS net FROM financial_payment p JOIN"
                                + " financial_account a ON a.id=p.account_id WHERE a.store_id=? AND"
                                + " p.occurred_at>=? AND p.occurred_at<? GROUP BY p.method ORDER BY"
                                + " p.method",
                        actor.storeId(),
                        from,
                        to));
        totals.put(
                "days",
                db.rows(
                        "SELECT (confirmed_at AT TIME ZONE 'America/Sao_Paulo')::date AS"
                            + " day,count(*) AS sales,sum(total) AS revenue FROM sale WHERE"
                            + " store_id=? AND confirmed_at>=? AND confirmed_at<? AND status NOT IN"
                            + " ('CANCELLED','EXPIRED','QUOTE','PENDING_PAYMENT') GROUP BY day"
                            + " ORDER BY day",
                        actor.storeId(),
                        from,
                        to));
        totals.put(
                "products",
                db.rows(
                        "SELECT i.product_id,i.code,i.name,i.unit_code,sum(i.quantity) AS"
                            + " quantity,sum(i.line_total) AS gross_revenue FROM sale_item i JOIN"
                            + " sale s ON s.id=i.sale_id WHERE s.store_id=? AND s.confirmed_at>=?"
                            + " AND s.confirmed_at<? AND s.status NOT IN"
                            + " ('CANCELLED','EXPIRED','QUOTE','PENDING_PAYMENT') GROUP BY"
                            + " i.product_id,i.code,i.name,i.unit_code ORDER BY gross_revenue DESC"
                            + " LIMIT 30",
                        actor.storeId(),
                        from,
                        to));
        // Custos históricos dos itens: nunca recalcular pelo custo atual do cadastro.
        var economics = db.one("""
            SELECT coalesce(sum(s.subtotal-s.discount),0) AS merchandise_revenue,
                   coalesce(sum(s.delivery_fee),0) AS delivery_fees
            FROM sale s WHERE s.store_id=? AND s.confirmed_at>=? AND s.confirmed_at<?
              AND s.status NOT IN ('CANCELLED','EXPIRED','QUOTE','PENDING_PAYMENT')
            """, actor.storeId(), from, to);
        var goods = db.one("""
            SELECT coalesce(sum(i.quantity*i.unit_cost),0) AS goods_cost
            FROM sale_item i JOIN sale s ON s.id=i.sale_id
            WHERE s.store_id=? AND s.confirmed_at>=? AND s.confirmed_at<?
              AND s.status NOT IN ('CANCELLED','EXPIRED','QUOTE','PENDING_PAYMENT')
            """, actor.storeId(), from, to);
        BigDecimal merchandiseRevenue = new BigDecimal(economics.get("merchandiseRevenue").toString());
        BigDecimal goodsCost = new BigDecimal(goods.get("goodsCost").toString());
        BigDecimal grossProfit = merchandiseRevenue.subtract(goodsCost);
        totals.put("merchandiseRevenue", merchandiseRevenue.toPlainString());
        totals.put("goodsCost", goodsCost.toPlainString());
        totals.put("deliveryFees", economics.get("deliveryFees"));
        totals.put("grossProfit", grossProfit.toPlainString());
        totals.put("grossMarginPercent", merchandiseRevenue.signum() == 0 ? null
                : grossProfit.multiply(new BigDecimal("100"))
                    .divide(merchandiseRevenue, 1, RoundingMode.HALF_UP).toPlainString());

        // Comparação com o mesmo número de dias imediatamente anterior ao intervalo.
        long daysInPeriod = ChronoUnit.DAYS.between(start, end) + 1;
        var previous = db.one("""
            SELECT coalesce(sum(total),0) AS revenue
            FROM sale WHERE store_id=? AND confirmed_at>=? AND confirmed_at<?
              AND status NOT IN ('CANCELLED','EXPIRED','QUOTE','PENDING_PAYMENT')
            """, actor.storeId(), start.minusDays(daysInPeriod).atStartOfDay(Db.ZONE).toOffsetDateTime(), from);
        BigDecimal previousRevenue = new BigDecimal(previous.get("revenue").toString());
        BigDecimal currentRevenue = new BigDecimal(totals.get("revenue").toString());
        totals.put("previousRevenue", previousRevenue.toPlainString());
        totals.put("revenueChangePercent", previousRevenue.signum() == 0 ? null
                : currentRevenue.subtract(previousRevenue).multiply(new BigDecimal("100"))
                    .divide(previousRevenue, 1, RoundingMode.HALF_UP).toPlainString());

        // Valor imobilizado é estimativa de custo do estoque físico, não lucro nem caixa.
        var stock = db.one("""
            SELECT coalesce(sum(b.physical*p.cost),0) AS stock_cost_value,
                   count(DISTINCT CASE WHEN b.physical>0 THEN p.id END) AS stocked_products
            FROM inventory_balance b
            JOIN inventory_location l ON l.id=b.location_id
            JOIN product p ON p.id=b.product_id
            WHERE l.store_id=? AND p.active
            """, actor.storeId());
        totals.put("stockCostValue", stock.get("stockCostValue"));
        totals.put("stockedProducts", stock.get("stockedProducts"));

        // Somente produtos com mais de 90 dias de cadastro e estoque positivo.
        // Evita classificar produtos novos como "parados".
        String dormantFilter = """
            FROM product p
            JOIN inventory_balance b ON b.product_id=p.id
            JOIN inventory_location l ON l.id=b.location_id
            WHERE l.store_id=? AND p.active AND p.created_at < now()-interval '90 days'
              AND b.physical>0
              AND NOT EXISTS (
                SELECT 1 FROM sale_item si JOIN sale s ON s.id=si.sale_id
                WHERE si.product_id=p.id AND s.store_id=l.store_id
                  AND s.confirmed_at >= now()-interval '90 days'
                  AND s.status NOT IN ('CANCELLED','EXPIRED','QUOTE','PENDING_PAYMENT'))
            """;
        totals.put("dormantProducts", db.one(
                "SELECT count(DISTINCT p.id) AS total " + dormantFilter,
                actor.storeId()).get("total"));
        totals.put("dormantSamples", db.rows(
                "SELECT p.id,p.name,p.brand,sum(b.physical) AS quantity,p.cost "
                + dormantFilter
                + " GROUP BY p.id,p.name,p.brand,p.cost ORDER BY quantity DESC,p.name LIMIT 10",
                actor.storeId()));
        return totals;
    }

    public String csv(Actor actor, LocalDate start, LocalDate end) {
        var report = report(actor, start, end);
        var csv = new StringBuilder("\uFEFFData;Vendas;Valor das vendas\r\n");
        for (Object object : (List<?>) report.get("days")) {
            var row = (Map<?, ?>) object;
            csv.append(cell(row.get("day")))
                    .append(';')
                    .append(cell(row.get("sales")))
                    .append(';')
                    .append(cell(row.get("revenue")))
                    .append("\r\n");
        }
        return csv.toString();
    }

    private String cell(Object value) {
        String text = String.valueOf(value);
        if (text.matches("^[=+@-].*")) text = "'" + text;
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}
