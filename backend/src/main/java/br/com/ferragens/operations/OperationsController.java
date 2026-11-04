package br.com.ferragens.operations;

import br.com.ferragens.auth.*;
import br.com.ferragens.commerce.*;
import br.com.ferragens.shared.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('OWNER','MANAGER')")
public class OperationsController {
    private final Db db;
    private final CommandGate gate;
    private final CurrentActorService actors;

    public OperationsController(Db db, CommandGate gate, CurrentActorService actors) {
        this.db = db;
        this.gate = gate;
        this.actors = actors;
    }

    public record Settings(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 40) String phone,
            @Size(max = 500) String address,
            @NotNull BigDecimal deliveryFee,
            boolean deliveryEnabled,
            @NotBlank @Size(max = 1000) String paymentInstructions,
            @Min(15) @Max(10080) int reservationMinutes) {}

    public record Reference(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 12) String code,
            boolean active,
            @Min(0) @Max(4) int quantityScale,
            BigDecimal quantityStep) {}

    @GetMapping("/settings")
    public Map<String, Object> settings(Authentication auth) {
        return db.one(
                "SELECT s.name,c.* FROM store s JOIN store_settings c ON c.store_id=s.id WHERE"
                        + " s.id=?",
                actors.require(auth).storeId());
    }

    @PutMapping("/settings")
    @Transactional
    public Map<String, Object> settings(Authentication auth, @Valid @RequestBody Settings body) {
        var actor = actors.require(auth);
        db.jdbc.update("UPDATE store SET name=? WHERE id=?", body.name().trim(), actor.storeId());
        db.jdbc.update(
                "UPDATE store_settings SET"
                    + " phone=?,address=?,delivery_fee=?,delivery_enabled=?,payment_instructions=?,reservation_minutes=?"
                    + " WHERE store_id=?",
                Optional.ofNullable(body.phone()).orElse(""),
                Optional.ofNullable(body.address()).orElse(""),
                Db.money(body.deliveryFee(), false),
                body.deliveryEnabled(),
                Db.required(body.paymentInstructions(), 1000, "as instruções"),
                body.reservationMinutes(),
                actor.storeId());
        db.audit(actor, "STORE_SETTINGS", actor.storeId(), "Configurações da loja atualizadas");
        return settings(auth);
    }

    @GetMapping("/audit")
    public Map<String, Object> audit(
            Authentication auth, @RequestParam(defaultValue = "0") int page) {
        var actor = actors.require(auth);
        return db.page(
                "SELECT * FROM audit_event WHERE store_id=? ORDER BY occurred_at DESC,id DESC",
                "SELECT count(*) FROM audit_event WHERE store_id=?",
                page,
                30,
                actor.storeId());
    }

    @GetMapping("/references")
    public Map<String, Object> references() {
        return Map.of(
                "categories",
                db.rows("SELECT * FROM category ORDER BY name"),
                "units",
                db.rows("SELECT * FROM unit ORDER BY code"));
    }

    @PostMapping("/references/categories")
    @Transactional
    public Map<String, Object> category(
            Authentication auth,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody Reference body) {
        var actor = actors.require(auth);
        UUID id =
                gate.run(
                        actor,
                        key,
                        "category:create",
                        body,
                        () -> {
                            UUID target = UUID.randomUUID();
                            db.jdbc.update(
                                    "INSERT INTO category(id,name,active) VALUES(?,?,?)",
                                    target,
                                    Db.required(body.name(), 100, "o nome"),
                                    body.active());
                            db.audit(actor, "CATEGORY_CREATED", target, body.name());
                            return target;
                        });
        return db.one("SELECT * FROM category WHERE id=?", id);
    }

    @PutMapping("/references/categories/{id}")
    @Transactional
    public Map<String, Object> updateCategory(
            Authentication auth, @PathVariable UUID id, @Valid @RequestBody Reference body) {
        db.one("SELECT id FROM category WHERE id=?", id);
        db.jdbc.update(
                "UPDATE category SET name=?,active=? WHERE id=?",
                Db.required(body.name(), 100, "o nome"),
                body.active(),
                id);
        db.audit(actors.require(auth), "CATEGORY_UPDATED", id, body.name());
        return db.one("SELECT * FROM category WHERE id=?", id);
    }

    @PostMapping("/references/units")
    @Transactional
    public Map<String, Object> unit(Authentication auth, @Valid @RequestBody Reference body) {
        String code = Db.required(body.code(), 12, "o código").toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9]{1,12}")
                || body.quantityStep() == null
                || body.quantityStep().signum() <= 0
                || body.quantityStep().stripTrailingZeros().scale() > body.quantityScale()
                || body.quantityStep().compareTo(new BigDecimal("999999999999999")) > 0)
            throw new BusinessRuleException("Confira código, precisão e incremento da unidade.");
        db.jdbc.update(
                "INSERT INTO unit(code,name,active,quantity_scale,quantity_step) VALUES(?,?,?,?,?)",
                code,
                body.name(),
                body.active(),
                body.quantityScale(),
                body.quantityStep());
        db.audit(actors.require(auth), "UNIT_CREATED", null, "Unidade " + code);
        return db.one("SELECT * FROM unit WHERE code=?", code);
    }
}
