package br.com.ferragens.commerce;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class ReservationExpiry {
    private final Db db;
    private final SalesService sales;

    public ReservationExpiry(Db db, SalesService sales) {
        this.db = db;
        this.sales = sales;
    }

    @Scheduled(fixedDelayString = "${app.orders.expiry-ms:60000}")
    public void release() {
        for (var row :
                db.rows(
                        "SELECT id FROM sale WHERE status='PENDING_PAYMENT' AND expires_at<now()"
                                + " ORDER BY expires_at LIMIT 50")) sales.expire(Db.id(row, "id"));
    }
}
