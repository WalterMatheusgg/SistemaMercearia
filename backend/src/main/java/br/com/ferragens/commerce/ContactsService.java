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
            @Size(max = 160) String name,
            @Email @Size(max = 254) String email,
            @Size(max = 40) String phone,
            @Size(max = 30) String document,
            @Size(max = 500) String address,
            boolean active,
            @Size(max = 160) String representativeName,
            @Size(max = 40) String whatsapp,
            @Size(max = 120) String city,
            @Size(max = 1000) String products,
            @Size(max = 1000) String brands,
            @Size(max = 200) String minimumOrder,
            @Size(max = 500) String quantityDiscount,
            @Size(max = 500) String paymentTerms,
            @Size(max = 160) String deliveryTime,
            @Size(max = 300) String freightTerms,
            @Size(max = 1000) String returnPolicy,
            @Size(max = 1000) String warrantyPolicy) {}

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
                "SELECT id,name,email,phone,document,address,active,representative_name,whatsapp,city,products,brands,minimum_order,quantity_discount,payment_terms,delivery_time,freight_terms,return_policy,warranty_policy" + from + " ORDER BY name,id",
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
                            String name = body.name() != null && !body.name().isBlank()
                                    ? body.name().trim()
                                    : id == null
                                        ? (kind.equals("SUPPLIER") ? "Fornecedor " : "Cliente ")
                                          + target.toString().substring(0, 8)
                                        : db.one("SELECT name FROM party WHERE id=? AND store_id=? AND kind=?",
                                            id, actor.storeId(), kind).get("name").toString();
                            if (id == null)
                                db.jdbc.update(
                                        "INSERT INTO"
                                            + " party(id,store_id,kind,name,email,phone,document,address,active,representative_name,whatsapp,city,products,brands,minimum_order,quantity_discount,payment_terms,delivery_time,freight_terms,return_policy,warranty_policy)"
                                            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                                        target,
                                        actor.storeId(),
                                        kind,
                                        name,
                                        Db.optional(body.email(), 254),
                                        Db.optional(body.phone(), 40),
                                        Db.optional(body.document(), 30),
                                        Db.optional(body.address(), 500),
                                        body.active(),
                                        Db.optional(body.representativeName(), 160), Db.optional(body.whatsapp(), 40),
                                        Db.optional(body.city(), 120), Db.optional(body.products(), 1000),
                                        Db.optional(body.brands(), 1000), Db.optional(body.minimumOrder(), 200),
                                        Db.optional(body.quantityDiscount(), 500), Db.optional(body.paymentTerms(), 500),
                                        Db.optional(body.deliveryTime(), 160), Db.optional(body.freightTerms(), 300),
                                        Db.optional(body.returnPolicy(), 1000), Db.optional(body.warrantyPolicy(), 1000));
                            else {
                                db.one(
                                        "SELECT id FROM party WHERE id=? AND store_id=? AND kind=?"
                                                + " FOR UPDATE",
                                        id,
                                        actor.storeId(),
                                        kind);
                                db.jdbc.update(
                                        "UPDATE party SET"
                                            + " name=?,email=?,phone=?,document=?,address=?,active=?,representative_name=?,whatsapp=?,city=?,products=?,brands=?,minimum_order=?,quantity_discount=?,payment_terms=?,delivery_time=?,freight_terms=?,return_policy=?,warranty_policy=?,updated_at=now()"
                                            + " WHERE id=?",
                                        name,
                                        Db.optional(body.email(), 254),
                                        Db.optional(body.phone(), 40),
                                        Db.optional(body.document(), 30),
                                        Db.optional(body.address(), 500),
                                        body.active(),
                                        Db.optional(body.representativeName(), 160), Db.optional(body.whatsapp(), 40),
                                        Db.optional(body.city(), 120), Db.optional(body.products(), 1000),
                                        Db.optional(body.brands(), 1000), Db.optional(body.minimumOrder(), 200),
                                        Db.optional(body.quantityDiscount(), 500), Db.optional(body.paymentTerms(), 500),
                                        Db.optional(body.deliveryTime(), 160), Db.optional(body.freightTerms(), 300),
                                        Db.optional(body.returnPolicy(), 1000), Db.optional(body.warrantyPolicy(), 1000), id);
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
                        "SELECT id,kind,name,email,phone,document,address,active,representative_name,whatsapp,city,products,brands,minimum_order,quantity_discount,payment_terms,delivery_time,freight_terms,return_policy,warranty_policy FROM party WHERE"
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
