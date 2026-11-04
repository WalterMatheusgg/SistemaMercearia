package br.com.ferragens.commerce;

import br.com.ferragens.auth.CurrentActorService.Actor;
import br.com.ferragens.shared.BusinessRuleException;

import jakarta.validation.constraints.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class ContactsService {
    private final Db db;
    private final CommandGate gate;

    public ContactsService(Db db, CommandGate gate) {
        this.db = db;
        this.gate = gate;
    }

    public record Contact(
            @NotBlank @Size(max = 160) String name,
            @Email @Size(max = 254) String email,
            @Size(max = 40) String phone,
            @Size(max = 30) String document,
            @Size(max = 500) String address,
            boolean active) {}

    static String kind(String kind) {
        if (!Set.of("CUSTOMER", "SUPPLIER").contains(kind))
            throw new BusinessRuleException("Tipo de cadastro inválido.");
        return kind;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(Actor actor, String kind, String query, int page, int size) {
        kind(kind);
        String pattern =
                "%"
                        + (query == null ? "" : query.trim().toLowerCase(Locale.ROOT))
                                .replace("\\", "\\\\")
                                .replace("%", "\\%")
                                .replace("_", "\\_")
                        + "%";
        String from = " FROM party WHERE store_id=? AND kind=? AND lower(name) LIKE ? ESCAPE '\\'";
        return db.page(
                "SELECT id,name,email,phone,document,address,active" + from + " ORDER BY name,id",
                "SELECT count(*)" + from,
                page,
                size,
                actor.storeId(),
                kind,
                pattern);
    }

    public Map<String, Object> save(Actor actor, UUID key, String kind, UUID id, Contact body) {
        kind(kind);
        UUID result =
                gate.run(
                        actor,
                        key,
                        "contact:" + kind + ":" + id,
                        body,
                        () -> {
                            UUID target = id == null ? UUID.randomUUID() : id;
                            String name = Db.required(body.name(), 160, "o nome");
                            if (id == null)
                                db.jdbc.update(
                                        "INSERT INTO"
                                            + " party(id,store_id,kind,name,email,phone,document,address,active)"
                                            + " VALUES(?,?,?,?,?,?,?,?,?)",
                                        target,
                                        actor.storeId(),
                                        kind,
                                        name,
                                        Db.optional(body.email(), 254),
                                        Db.optional(body.phone(), 40),
                                        Db.optional(body.document(), 30),
                                        Db.optional(body.address(), 500),
                                        body.active());
                            else {
                                db.one(
                                        "SELECT id FROM party WHERE id=? AND store_id=? AND kind=?"
                                                + " FOR UPDATE",
                                        id,
                                        actor.storeId(),
                                        kind);
                                db.jdbc.update(
                                        "UPDATE party SET"
                                            + " name=?,email=?,phone=?,document=?,address=?,active=?,updated_at=now()"
                                            + " WHERE id=?",
                                        name,
                                        Db.optional(body.email(), 254),
                                        Db.optional(body.phone(), 40),
                                        Db.optional(body.document(), 30),
                                        Db.optional(body.address(), 500),
                                        body.active(),
                                        id);
                            }
                            db.audit(
                                    actor,
                                    id == null ? "CONTACT_CREATED" : "CONTACT_UPDATED",
                                    target,
                                    kind + ": " + name);
                            return target;
                        });
        return detail(actor, result, kind);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Actor actor, UUID id, String kind) {
        var result =
                db.one(
                        "SELECT id,kind,name,email,phone,document,address,active FROM party WHERE"
                                + " id=? AND store_id=? AND kind=?",
                        id,
                        actor.storeId(),
                        kind(kind));
        result.put(
                "sales",
                db.rows(
                        "SELECT id,channel,status,total,created_at FROM sale WHERE customer_id=?"
                                + " AND store_id=? ORDER BY created_at DESC LIMIT 100",
                        id,
                        actor.storeId()));
        result.put(
                "accounts",
                db.rows(
                        "SELECT id,direction,description,amount,paid,due_date,status FROM"
                            + " financial_account WHERE party_id=? AND store_id=? ORDER BY due_date"
                            + " DESC LIMIT 100",
                        id,
                        actor.storeId()));
        return result;
    }
}
