package br.com.ferragens.commerce;

import br.com.ferragens.auth.CurrentActorService.Actor;
import br.com.ferragens.shared.*;

import jakarta.validation.constraints.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class FinanceService {
    private final Db db;
    private final CommandGate gate;
    private final CashService cash;

    public FinanceService(Db db, CommandGate gate, CashService cash) {
        this.db = db;
        this.gate = gate;
        this.cash = cash;
    }

    public record Payment(
            @NotNull BigDecimal amount, @NotBlank String method, @Size(max = 500) String note) {}

    public record Account(
            @NotBlank String direction,
            @NotBlank @Size(max = 300) String description,
            UUID partyId,
            @NotNull BigDecimal amount,
            @NotNull LocalDate dueDate) {}

    @Transactional(readOnly = true)
    public Map<String, Object> list(Actor actor, String direction, String status, int page) {
        String where =
                " FROM financial_account a LEFT JOIN party p ON p.id=a.party_id WHERE a.store_id=?"
                        + " AND (?='' OR a.direction=?) AND (?='' OR a.status = ANY(string_to_array(?, ',')))";
        return db.page(
                "SELECT a.*,p.name AS party_name"
                        + where
                        + " ORDER BY a.due_date,a.created_at DESC",
                "SELECT count(*)" + where,
                page,
                20,
                actor.storeId(),
                direction,
                direction,
                status,
                status);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Actor actor, UUID id) {
        var result =
                db.one(
                        "SELECT a.*,p.name AS party_name FROM financial_account a LEFT JOIN party p"
                                + " ON p.id=a.party_id WHERE a.id=? AND a.store_id=?",
                        id,
                        actor.storeId());
        result.put(
                "payments",
                db.rows(
                        "SELECT p.*,u.username AS actor_name FROM financial_payment p JOIN app_user"
                            + " u ON u.id=p.actor_id WHERE account_id=? ORDER BY occurred_at,id",
                        id));
        return result;
    }

    public Map<String, Object> create(Actor actor, UUID key, Account body) {
        UUID id =
                gate.run(
                        actor,
                        key,
                        "account:create",
                        body,
                        () -> {
                            if (!Set.of("PAYABLE", "RECEIVABLE").contains(body.direction()))
                                throw new BusinessRuleException("Tipo de conta inválido.");
                            if (body.partyId() != null)
                                db.one(
                                        "SELECT id FROM party WHERE id=? AND store_id=?",
                                        body.partyId(),
                                        actor.storeId());
                            UUID result =
                                    createTitle(
                                            actor,
                                            body.partyId(),
                                            body.direction(),
                                            Db.required(body.description(), 300, "a descrição"),
                                            Db.money(body.amount(), true),
                                            body.dueDate(),
                                            null,
                                            null);
                            db.audit(actor, "ACCOUNT_CREATED", result, body.description());
                            return result;
                        });
        return detail(actor, id);
    }

    public Map<String, Object> settle(Actor actor, UUID key, UUID id, Payment body) {
        gate.run(actor, key, "account:settle:" + id, body, () -> pay(actor, id, body));
        return detail(actor, id);
    }

    public Map<String, Object> reverse(Actor actor, UUID key, UUID paymentId, String reason) {
        UUID account =
                Db.id(
                        db.one(
                                "SELECT p.account_id FROM financial_payment p JOIN"
                                    + " financial_account a ON a.id=p.account_id WHERE p.id=? AND"
                                    + " a.store_id=?",
                                paymentId,
                                actor.storeId()),
                        "accountId");
        gate.run(
                actor,
                key,
                "payment:reverse:" + paymentId,
                reason,
                () -> reversePayment(actor, paymentId, reason));
        return detail(actor, account);
    }

    UUID createTitle(
            Actor actor,
            UUID party,
            String direction,
            String description,
            BigDecimal amount,
            LocalDate due,
            UUID sale,
            UUID receipt) {
        UUID id = UUID.randomUUID();
        db.jdbc.update(
                "INSERT INTO"
                    + " financial_account(id,store_id,party_id,direction,description,amount,due_date,status,sale_id,receipt_id)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?)",
                id,
                actor.storeId(),
                party,
                direction,
                description,
                amount,
                due,
                amount.signum() == 0 ? "PAID" : "OPEN",
                sale,
                receipt);
        return id;
    }

    UUID pay(Actor actor, UUID account, Payment body) {
        BigDecimal amount = Db.money(body.amount(), true);
        if (!Set.of("CASH", "PIX", "DEBIT_CARD", "CREDIT_CARD", "TRANSFER").contains(body.method()))
            throw new BusinessRuleException("Meio de pagamento inválido.");
        var title =
                db.one(
                        "SELECT * FROM financial_account WHERE id=? AND store_id=? FOR UPDATE",
                        account,
                        actor.storeId());
        if (title.get("status").equals("CANCELLED"))
            throw new ConflictException("Conta cancelada.");
        BigDecimal next = Db.decimal(title, "paid").add(amount);
        if (next.compareTo(Db.decimal(title, "amount")) > 0)
            throw new ConflictException(
                    "O pagamento supera o saldo pendente. Registre somente o valor líquido, sem o"
                            + " troco.");
        String direction = title.get("direction").equals("RECEIVABLE") ? "IN" : "OUT";
        String note =
                Optional.ofNullable(Db.optional(body.note(), 500))
                        .orElse("Pagamento conferido pela loja");
        UUID entry =
                body.method().equals("CASH")
                        ? cash.add(
                                actor,
                                direction.equals("IN") ? amount : amount.negate(),
                                "PAYMENT",
                                note)
                        : null;
        UUID id = UUID.randomUUID();
        db.jdbc.update(
                "INSERT INTO"
                    + " financial_payment(id,account_id,amount,method,direction,actor_id,note,cash_entry_id)"
                    + " VALUES(?,?,?,?,?,?,?,?)",
                id,
                account,
                amount,
                body.method(),
                direction,
                actor.userId(),
                note,
                entry);
        updatePaid(account, next, Db.decimal(title, "amount"));
        db.audit(actor, "PAYMENT_RECORDED", id, body.method() + ": " + amount);
        return id;
    }

    UUID reversePayment(Actor actor, UUID id, String reason) {
        var p =
                db.one(
                        "SELECT p.*,a.store_id FROM financial_payment p JOIN financial_account a ON"
                                + " a.id=p.account_id WHERE p.id=? AND a.store_id=?",
                        id,
                        actor.storeId());
        UUID account = Db.id(p, "accountId");
        var title = db.one("SELECT * FROM financial_account WHERE id=? FOR UPDATE", account);
        p = db.one("SELECT * FROM financial_payment WHERE id=? FOR UPDATE", id);
        if (Boolean.TRUE.equals(p.get("reversed")) || p.get("reversalOf") != null)
            throw new ConflictException("Pagamento já estornado ou não estornável.");
        String note = Db.required(reason, 500, "o motivo do estorno");
        if (note.length() < 3)
            throw new BusinessRuleException("Informe um motivo com pelo menos 3 caracteres.");
        BigDecimal amount = Db.decimal(p, "amount");
        String direction = p.get("direction").equals("IN") ? "OUT" : "IN";
        UUID entry =
                p.get("method").equals("CASH")
                        ? cash.add(
                                actor,
                                direction.equals("IN") ? amount : amount.negate(),
                                "REFUND",
                                note)
                        : null;
        UUID reverse = UUID.randomUUID();
        db.jdbc.update(
                "INSERT INTO"
                    + " financial_payment(id,account_id,amount,method,direction,actor_id,note,reversal_of,cash_entry_id)"
                    + " VALUES(?,?,?,?,?,?,?,?,?)",
                reverse,
                account,
                amount,
                p.get("method"),
                direction,
                actor.userId(),
                note,
                id,
                entry);
        db.jdbc.update("UPDATE financial_payment SET reversed=true WHERE id=?", id);
        updatePaid(
                account, Db.decimal(title, "paid").subtract(amount), Db.decimal(title, "amount"));
        db.audit(actor, "PAYMENT_REVERSED", reverse, note);
        return reverse;
    }

    void cancelTitle(Actor actor, UUID sale, String reason) {
        var titles =
                db.rows(
                        "SELECT id FROM financial_account WHERE sale_id=? AND store_id=? FOR"
                                + " UPDATE",
                        sale,
                        actor.storeId());
        if (titles.isEmpty()) return;
        UUID id = Db.id(titles.getFirst(), "id");
        for (var payment :
                db.rows(
                        "SELECT id FROM financial_payment WHERE account_id=? AND NOT reversed AND"
                                + " reversal_of IS NULL ORDER BY occurred_at,id",
                        id)) reversePayment(actor, Db.id(payment, "id"), reason);
        db.jdbc.update("UPDATE financial_account SET status='CANCELLED' WHERE id=?", id);
    }

    void cancelReceipt(Actor actor, UUID receipt, String reason) {
        var title =
                db.one(
                        "SELECT id FROM financial_account WHERE receipt_id=? AND store_id=? FOR"
                                + " UPDATE",
                        receipt,
                        actor.storeId());
        UUID id = Db.id(title, "id");
        for (var payment :
                db.rows(
                        "SELECT id FROM financial_payment WHERE account_id=? AND NOT reversed AND"
                                + " reversal_of IS NULL ORDER BY occurred_at,id",
                        id)) reversePayment(actor, Db.id(payment, "id"), reason);
        db.jdbc.update("UPDATE financial_account SET status='CANCELLED' WHERE id=?", id);
    }

    private void updatePaid(UUID id, BigDecimal paid, BigDecimal total) {
        db.jdbc.update(
                "UPDATE financial_account SET paid=?,status=? WHERE id=?",
                paid,
                paid.compareTo(total) == 0 ? "PAID" : paid.signum() == 0 ? "OPEN" : "PARTIAL",
                id);
    }
}
