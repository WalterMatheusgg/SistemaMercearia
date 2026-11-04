package br.com.ferragens.commerce;
import br.com.ferragens.auth.CurrentActorService.Actor;
import br.com.ferragens.shared.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class ReportingService {
    private final Db db;
    public ReportingService(Db db){this.db=db;}
    public Map<String,Object> report(Actor actor,LocalDate start,LocalDate end){if(start==null)start=Db.today().withDayOfMonth(1);if(end==null)end=Db.today();if(end.isBefore(start)||end.isAfter(start.plusYears(2)))throw new BusinessRuleException("Selecione um intervalo de até dois anos.");var from=start.atStartOfDay(Db.ZONE).toOffsetDateTime();var to=end.plusDays(1).atStartOfDay(Db.ZONE).toOffsetDateTime();
        var totals=db.one("SELECT count(*) AS sales,coalesce(sum(total),0) AS revenue,coalesce(sum(discount),0) AS discounts,coalesce(avg(total),0)::numeric(14,2) AS average_ticket FROM sale WHERE store_id=? AND confirmed_at>=? AND confirmed_at<? AND status NOT IN ('CANCELLED','EXPIRED','QUOTE','PENDING_PAYMENT')",actor.storeId(),from,to);
        totals.put("start",start.toString());totals.put("end",end.toString());totals.put("receivables",db.one("SELECT coalesce(sum(amount-paid),0) AS pending FROM financial_account WHERE store_id=? AND direction='RECEIVABLE' AND status IN ('OPEN','PARTIAL')",actor.storeId()).get("pending"));totals.put("payables",db.one("SELECT coalesce(sum(amount-paid),0) AS pending FROM financial_account WHERE store_id=? AND direction='PAYABLE' AND status IN ('OPEN','PARTIAL')",actor.storeId()).get("pending"));
        totals.put("payments",db.rows("SELECT p.method,coalesce(sum(CASE WHEN p.direction='IN' THEN p.amount ELSE -p.amount END),0) AS net FROM financial_payment p JOIN financial_account a ON a.id=p.account_id WHERE a.store_id=? AND p.occurred_at>=? AND p.occurred_at<? GROUP BY p.method ORDER BY p.method",actor.storeId(),from,to));
        totals.put("days",db.rows("SELECT (confirmed_at AT TIME ZONE 'America/Sao_Paulo')::date AS day,count(*) AS sales,sum(total) AS revenue FROM sale WHERE store_id=? AND confirmed_at>=? AND confirmed_at<? AND status NOT IN ('CANCELLED','EXPIRED','QUOTE','PENDING_PAYMENT') GROUP BY day ORDER BY day",actor.storeId(),from,to));
        totals.put("products",db.rows("SELECT i.product_id,i.code,i.name,i.unit_code,sum(i.quantity) AS quantity,sum(i.line_total) AS gross_revenue FROM sale_item i JOIN sale s ON s.id=i.sale_id WHERE s.store_id=? AND s.confirmed_at>=? AND s.confirmed_at<? AND s.status NOT IN ('CANCELLED','EXPIRED','QUOTE','PENDING_PAYMENT') GROUP BY i.product_id,i.code,i.name,i.unit_code ORDER BY gross_revenue DESC LIMIT 30",actor.storeId(),from,to));return totals;
    }
    public String csv(Actor actor,LocalDate start,LocalDate end){var report=report(actor,start,end);var csv=new StringBuilder("\uFEFFData;Vendas;Valor das vendas\r\n");for(Object object:(List<?>)report.get("days")){var row=(Map<?,?>)object;csv.append(cell(row.get("day"))).append(';').append(cell(row.get("sales"))).append(';').append(cell(row.get("revenue"))).append("\r\n");}return csv.toString();}
    private String cell(Object value){String text=String.valueOf(value);if(text.matches("^[=+@-].*"))text="'"+text;return '"'+text.replace("\"","\"\"")+'"';}
}
