package br.com.ferragens.notification;

import br.com.ferragens.commerce.Db;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxWorker {
    private final Db db;
    private final JavaMailSender mail;
    private final String from;

    public OutboxWorker(
            Db db,
            JavaMailSender mail,
            @Value("${app.mail.from:sistema@ferragens.local}") String from) {
        this.db = db;
        this.mail = mail;
        this.from = from;
    }

    @Scheduled(fixedDelayString = "${app.mail.poll-ms:5000}")
    public void deliver() {
        UUID lease = UUID.randomUUID();
        var jobs =
                db.rows(
                        "WITH picked AS (SELECT id FROM notification_outbox WHERE (status='PENDING'"
                                + " AND next_attempt_at<=now()) OR (status='PROCESSING' AND"
                                + " locked_until<now()) ORDER BY created_at FOR UPDATE SKIP LOCKED"
                                + " LIMIT 10) UPDATE notification_outbox o SET"
                                + " status='PROCESSING',lease_id=?,locked_until=now()+interval '2"
                                + " minutes',attempts=attempts+1 FROM picked WHERE o.id=picked.id"
                                + " RETURNING o.*",
                        lease);
        for (var job : jobs) {
            UUID id = Db.id(job, "id");
            var users =
                    db.rows(
                            "SELECT"
                                + " u.email,u.enabled,u.email_verified,n.sale_completed,n.stock_low,n.daily_summary"
                                + " FROM app_user u LEFT JOIN notification_preference n ON"
                                + " n.user_id=u.id WHERE u.id=?",
                            Db.id(job, "userId"));
            boolean allowed =
                    !users.isEmpty()
                            && Boolean.TRUE.equals(users.getFirst().get("enabled"))
                            && Db.text(users.getFirst(), "email").equals(job.get("recipient"));
            if (allowed && !job.get("eventType").equals("TRANSACTIONAL")) {
                String preference =
                        switch (Db.text(job, "eventType")) {
                            case "SALE_COMPLETED" -> "saleCompleted";
                            case "STOCK_LOW" -> "stockLow";
                            default -> "dailySummary";
                        };
                allowed =
                        Boolean.TRUE.equals(users.getFirst().get("emailVerified"))
                                && Boolean.TRUE.equals(users.getFirst().get(preference));
            }
            if (allowed && !job.get("eventType").equals("TRANSACTIONAL"))
                allowed =
                        Boolean.TRUE.equals(
                                db.jdbc.queryForObject(
                                        "SELECT EXISTS(SELECT 1 FROM user_role WHERE user_id=? AND"
                                            + " (role_code IN ('ROLE_OWNER','ROLE_MANAGER') OR"
                                            + " (?='STOCK_LOW' AND role_code='ROLE_STOCKKEEPER')))",
                                        Boolean.class,
                                        Db.id(job, "userId"),
                                        job.get("eventType")));
            if (!allowed) {
                db.jdbc.update(
                        "UPDATE notification_outbox SET status='CANCELLED',locked_until=NULL WHERE"
                                + " id=? AND lease_id=?",
                        id,
                        lease);
                continue;
            }
            try {
                var message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(Db.text(job, "recipient"));
                message.setSubject(Db.text(job, "subject"));
                message.setText(Db.text(job, "body"));
                mail.send(message);
                db.jdbc.update(
                        "UPDATE notification_outbox SET"
                            + " status='SENT',sent_at=now(),locked_until=NULL,last_error=NULL WHERE"
                            + " id=? AND lease_id=?",
                        id,
                        lease);
            } catch (Exception e) {
                int attempt = ((Number) job.get("attempts")).intValue();
                db.jdbc.update(
                        "UPDATE notification_outbox SET status=?,next_attempt_at=now()+(?*interval"
                                + " '1 minute'),locked_until=NULL,last_error=? WHERE id=? AND"
                                + " lease_id=?",
                        attempt >= 5 ? "FAILED" : "PENDING",
                        Math.min(60, 1 << Math.min(attempt, 6)),
                        "Falha no SMTP. Confira configuração/conectividade do servidor.",
                        id,
                        lease);
            }
        }
    }

    @Scheduled(cron = "${app.mail.summary-cron:0 0 21 * * *}", zone = "America/Sao_Paulo")
    public void summary() {
        for (var store : db.rows("SELECT id FROM store")) {
            UUID id = Db.id(store, "id");
            var totals =
                    db.one(
                            "SELECT count(*) AS sales,coalesce(sum(total),0) AS revenue FROM sale"
                                    + " WHERE store_id=? AND confirmed_at>=? AND confirmed_at<? AND"
                                    + " status NOT IN"
                                    + " ('CANCELLED','EXPIRED','QUOTE','PENDING_PAYMENT')",
                            id,
                            Db.today().atStartOfDay(Db.ZONE).toOffsetDateTime(),
                            Db.today().plusDays(1).atStartOfDay(Db.ZONE).toOffsetDateTime());
            new Notifications(db)
                    .event(
                            id,
                            "summary:" + Db.today(),
                            "DAILY_SUMMARY",
                            "Resumo da loja · " + Db.today(),
                            "Vendas confirmadas: "
                                    + totals.get("sales")
                                    + "\nValor das vendas: R$ "
                                    + totals.get("revenue")
                                    + "\n"
                                    + "Pagamentos e recebíveis podem ser conferidos no"
                                    + " financeiro.");
        }
    }
}
