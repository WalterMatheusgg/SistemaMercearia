package br.com.ferragens.commerce;

import br.com.ferragens.auth.CurrentActorService.Actor;
import br.com.ferragens.shared.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
public class SupplierOffersService {
    private final Db db;
    private final CommandGate gate;

    public SupplierOffersService(Db db, CommandGate gate) {
        this.db = db;
        this.gate = gate;
    }

    public record Tier(@NotNull @DecimalMin("0.0001") BigDecimal minimumQuantity,
                       @NotNull @DecimalMin("0.00") BigDecimal unitCost) {}

    public record Offer(@NotNull UUID supplierId, @NotNull UUID productId,
                        @Size(max=80) String brand, @Size(max=80) String supplierProductCode,
                        @NotNull @DecimalMin("0.00") BigDecimal unitCost,
                        @NotNull @DecimalMin("0.0001") BigDecimal minimumOrderQuantity,
                        LocalDate quotedAt, LocalDate validUntil,
                        @Min(0) @Max(3650) Integer deliveryDays,
                        @NotNull @DecimalMin("0.00") BigDecimal freightCost,
                        @Size(max=500) String notes, boolean active,
                        @Size(max=30) List<@Valid Tier> tiers) {}

    @Transactional(readOnly=true)
    public Map<String,Object> list(Actor actor, UUID productId, UUID supplierId, boolean includeInactive) {
        StringBuilder sql = new StringBuilder(
            "SELECT o.*,p.code AS product_code,p.name AS product_name,p.base_unit_code,"+
            "s.name AS supplier_name FROM supplier_offer o JOIN product p ON p.id=o.product_id "+
            "JOIN party s ON s.id=o.supplier_id WHERE o.store_id=?");
        List<Object> args = new ArrayList<>();
        args.add(actor.storeId());
        if (productId != null) { sql.append(" AND o.product_id=?"); args.add(productId); }
        if (supplierId != null) { sql.append(" AND o.supplier_id=?"); args.add(supplierId); }
        if (!includeInactive) sql.append(" AND o.active AND (o.valid_until IS NULL OR o.valid_until>=CURRENT_DATE)");
        sql.append(" ORDER BY p.name,s.name,o.quoted_at DESC,o.id");
        List<Map<String,Object>> offers = db.rows(sql.toString(), args.toArray());
        for (var offer : offers) offer.put("tiers",
            db.rows("SELECT id,minimum_quantity,unit_cost FROM supplier_offer_tier WHERE offer_id=? ORDER BY minimum_quantity",
                    Db.id(offer,"id")));
        return Map.of("content", offers);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> compare(Actor actor, UUID productId, BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0)
            throw new BusinessRuleException("Informe uma quantidade maior que zero para comparar.");
        return Map.of("content", db.rows(
            "SELECT o.id,o.supplier_id,s.name AS supplier_name,o.product_id,p.name AS product_name,"+
            "p.base_unit_code,o.brand,o.unit_cost AS base_unit_cost,o.minimum_order_quantity,"+
            "o.freight_cost,o.delivery_days,o.quoted_at,o.valid_until,o.notes,"+
            "COALESCE(t.unit_cost,o.unit_cost) AS effective_unit_cost,"+
            "round(COALESCE(t.unit_cost,o.unit_cost)*?+o.freight_cost,2) AS estimated_total,"+
            "CASE WHEN o.valid_until IS NOT NULL AND o.valid_until<CURRENT_DATE THEN false ELSE true END AS valid "+
            "FROM supplier_offer o JOIN party s ON s.id=o.supplier_id JOIN product p ON p.id=o.product_id "+
            "LEFT JOIN LATERAL (SELECT unit_cost FROM supplier_offer_tier WHERE offer_id=o.id "+
            "AND minimum_quantity<=? ORDER BY minimum_quantity DESC LIMIT 1) t ON true "+
            "WHERE o.store_id=? AND o.product_id=? AND o.active AND s.active "+
            "AND (o.valid_until IS NULL OR o.valid_until>=CURRENT_DATE) "+
            "AND o.minimum_order_quantity<=? ORDER BY (COALESCE(t.unit_cost,o.unit_cost)*?+o.freight_cost),o.delivery_days NULLS LAST,s.name",
            quantity, quantity, actor.storeId(), productId, quantity, quantity));
    }

    public Map<String,Object> save(Actor actor, UUID key, UUID id, Offer body) {
        UUID saved = gate.run(actor,key,"supplier-offer:"+id,body,()->{
            var supplier=db.one("SELECT id FROM party WHERE id=? AND store_id=? AND kind='SUPPLIER' AND active FOR UPDATE",
                body.supplierId(),actor.storeId());
            var product=db.one("SELECT id FROM product WHERE id=? AND active FOR UPDATE",body.productId());
            LocalDate quoted=body.quotedAt()==null?Db.today():body.quotedAt();
            if(body.validUntil()!=null && body.validUntil().isBefore(quoted))
                throw new BusinessRuleException("A validade não pode ser anterior à data da cotação.");
            UUID target=id==null?UUID.randomUUID():id;
            if(id==null) db.jdbc.update(
                "INSERT INTO supplier_offer(id,store_id,supplier_id,product_id,brand,supplier_product_code,unit_cost,minimum_order_quantity,quoted_at,valid_until,delivery_days,freight_cost,notes,active,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                target,actor.storeId(),body.supplierId(),body.productId(),Db.optional(body.brand(),80),
                Db.optional(body.supplierProductCode(),80),Db.money(body.unitCost(),false),
                body.minimumOrderQuantity(),quoted,body.validUntil(),body.deliveryDays(),
                Db.money(body.freightCost(),false),Db.optional(body.notes(),500),body.active(),actor.userId());
            else {
                db.one("SELECT id FROM supplier_offer WHERE id=? AND store_id=? FOR UPDATE",id,actor.storeId());
                db.jdbc.update("UPDATE supplier_offer SET supplier_id=?,product_id=?,brand=?,supplier_product_code=?,unit_cost=?,minimum_order_quantity=?,quoted_at=?,valid_until=?,delivery_days=?,freight_cost=?,notes=?,active=?,updated_at=now() WHERE id=?",
                    body.supplierId(),body.productId(),Db.optional(body.brand(),80),Db.optional(body.supplierProductCode(),80),
                    Db.money(body.unitCost(),false),body.minimumOrderQuantity(),quoted,body.validUntil(),
                    body.deliveryDays(),Db.money(body.freightCost(),false),Db.optional(body.notes(),500),body.active(),id);
                db.jdbc.update("DELETE FROM supplier_offer_tier WHERE offer_id=?",id);
            }
            Set<BigDecimal> thresholds=new HashSet<>();
            if(body.tiers()!=null) for(var tier:body.tiers()) {
                if(tier.minimumQuantity().compareTo(body.minimumOrderQuantity())<0 ||
                   !thresholds.add(tier.minimumQuantity()))
                    throw new BusinessRuleException("Faixas de preço devem ser únicas e não inferiores ao pedido mínimo.");
                db.jdbc.update("INSERT INTO supplier_offer_tier(id,offer_id,minimum_quantity,unit_cost) VALUES(?,?,?,?)",
                    UUID.randomUUID(),target,tier.minimumQuantity(),Db.money(tier.unitCost(),false));
            }
            db.audit(actor,id==null?"SUPPLIER_OFFER_CREATED":"SUPPLIER_OFFER_UPDATED",target,
                "Cotação de fornecedor para produto "+body.productId());
            return target;
        });
        return detail(actor,saved);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> detail(Actor actor, UUID id) {
        var row=db.one("SELECT * FROM supplier_offer WHERE id=? AND store_id=?",id,actor.storeId());
        row.put("tiers",db.rows("SELECT id,minimum_quantity,unit_cost FROM supplier_offer_tier WHERE offer_id=? ORDER BY minimum_quantity",id));
        return row;
    }

    public Map<String,Object> deactivate(Actor actor, UUID key, UUID id) {
        gate.run(actor,key,"supplier-offer-deactivate:"+id,Map.of("id",id),()->{
            db.jdbc.update("UPDATE supplier_offer SET active=false,updated_at=now() WHERE id=? AND store_id=?",id,actor.storeId());
            db.audit(actor,"SUPPLIER_OFFER_DEACTIVATED",id,"Cotação desativada");
            return id;
        });
        return detail(actor,id);
    }
}
