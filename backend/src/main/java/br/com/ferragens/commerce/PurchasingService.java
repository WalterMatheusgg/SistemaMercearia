package br.com.ferragens.commerce;

import br.com.ferragens.auth.CurrentActorService.Actor;
import br.com.ferragens.inventory.*;
import br.com.ferragens.inventory.InventoryModels.*;
import br.com.ferragens.shared.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class PurchasingService {
    private final Db db;
    private final CommandGate gate;
    private final CatalogService catalog;
    private final InventoryService inventory;
    private final FinanceService finance;
    private final ProductCostingService costing;

    public PurchasingService(
            Db db,
            CommandGate gate,
            CatalogService catalog,
            InventoryService inventory,
            FinanceService finance,
            ProductCostingService costing) {
        this.db = db;
        this.gate = gate;
        this.catalog = catalog;
        this.inventory = inventory;
        this.finance = finance;
        this.costing = costing;
    }

    public record Item(
            @NotNull UUID productId, @NotNull BigDecimal quantity, @NotNull BigDecimal unitCost) {}

    public record Order(
            @NotNull UUID supplierId,
            @NotEmpty @Size(max = 100) List<@Valid Item> items,
            @NotNull LocalDate dueDate,
            @Size(max = 500) String note,
            @DecimalMin("0.00") BigDecimal freightCost) {
        public Order(UUID supplierId, List<Item> items, LocalDate dueDate, String note) {
            this(supplierId, items, dueDate, note, BigDecimal.ZERO);
        }
    }

    public record Received(@NotNull UUID itemId, @NotNull BigDecimal quantity) {}

    public record Receipt(
            @NotEmpty @Size(max = 100) List<@Valid Received> items,
            @NotBlank @Size(min = 3, max = 500) String note) {}

    @Transactional(readOnly = true)
    public Map<String, Object> list(Actor actor, int page) {
        String from = " FROM purchase_order WHERE store_id=?";
        return db.page(
                "SELECT *" + from + " ORDER BY created_at DESC,id DESC",
                "SELECT count(*)" + from,
                page,
                20,
                actor.storeId());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Actor actor, UUID id) {
        var order =
                db.one(
                        "SELECT * FROM purchase_order WHERE id=? AND store_id=?",
                        id,
                        actor.storeId());
        order.put(
                "items",
                db.rows("SELECT * FROM purchase_item WHERE purchase_id=? ORDER BY name,id", id));
        order.put(
                "receipts",
                db.rows(
                        "SELECT r.*,u.username AS actor_name FROM purchase_receipt r JOIN app_user"
                            + " u ON u.id=r.actor_id WHERE purchase_id=? ORDER BY received_at DESC",
                        id));
        return order;
    }

    public Map<String, Object> create(Actor actor, UUID key, Order body) {
        UUID id =
                gate.run(
                        actor,
                        key,
                        "purchase:create",
                        body,
                        () -> {
                            String supplier =
                                    Db.text(
                                            db.one(
                                                    "SELECT name FROM party WHERE id=? AND"
                                                            + " store_id=? AND kind='SUPPLIER' AND"
                                                            + " active",
                                                    body.supplierId(),
                                                    actor.storeId()),
                                            "name");
                            if (body.items() == null
                                    || body.items().isEmpty()
                                    || body.items().size() > 100
                                    || body.dueDate() == null)
                                throw new BusinessRuleException(
                                        "Informe fornecedor, itens e vencimento.");
                            UUID order = UUID.randomUUID();
                            BigDecimal total = BigDecimal.ZERO;
                            var values = new ArrayList<Object[]>();
                            Set<UUID> ids = new HashSet<>();
                            for (var item :
                                    body.items().stream()
                                            .sorted(Comparator.comparing(Item::productId))
                                            .toList()) {
                                if (!ids.add(item.productId()))
                                    throw new BusinessRuleException(
                                            "Agrupe cada produto em uma única linha.");
                                var p = catalog.lockProduct(item.productId());
                                if (!Boolean.TRUE.equals(p.get("active")))
                                    throw new BusinessRuleException("Produto inativo.");
                                catalog.quantity(p, item.quantity());
                                BigDecimal cost = Db.money(item.unitCost(), false),
                                        line =
                                                Db.money(
                                                        cost.multiply(item.quantity())
                                                                .setScale(2, RoundingMode.HALF_UP),
                                                        false);
                                total = total.add(line);
                                values.add(
                                        new Object[] {
                                            UUID.randomUUID(),
                                            order,
                                            item.productId(),
                                            p.get("code"),
                                            p.get("name"),
                                            p.get("baseUnitCode"),
                                            item.quantity(),
                                            cost,
                                            line
                                        });
                            }
                            BigDecimal freight = Db.money(
                                    body.freightCost() == null ? BigDecimal.ZERO : body.freightCost(), false);
                            total = total.add(freight);
                            db.jdbc.update(
                                    "INSERT INTO"
                                        + " purchase_order(id,store_id,supplier_id,supplier_name,total,freight_cost,due_date,note,created_by)"
                                        + " VALUES(?,?,?,?,?,?,?,?,?)",
                                    order,
                                    actor.storeId(),
                                    body.supplierId(),
                                    supplier,
                                    Db.money(total, false),
                                    freight,
                                    body.dueDate(),
                                    Db.optional(body.note(), 500),
                                    actor.userId());
                            for (var item : values)
                                db.jdbc.update(
                                        "INSERT INTO"
                                            + " purchase_item(id,purchase_id,product_id,code,name,unit_code,quantity,unit_cost,line_total)"
                                            + " VALUES(?,?,?,?,?,?,?,?,?)",
                                        item);
                            db.audit(
                                    actor,
                                    "PURCHASE_CREATED",
                                    order,
                                    "Pedido ao fornecedor " + supplier);
                            return order;
                        });
        return detail(actor, id);
    }

    public Map<String, Object> receive(Actor actor, UUID key, UUID id, Receipt body) {
        gate.run(
                actor,
                key,
                "purchase:receive:" + id,
                body,
                () -> {
                    var order =
                            db.one(
                                    "SELECT * FROM purchase_order WHERE id=? AND store_id=? FOR"
                                            + " UPDATE",
                                    id,
                                    actor.storeId());
                    if (!Set.of("OPEN", "PARTIAL").contains(order.get("status")))
                        throw new ConflictException("Pedido já encerrado.");
                    if (body.items() == null || body.items().isEmpty() || body.items().size() > 100)
                        throw new BusinessRuleException("Informe os itens efetivamente recebidos.");
                    String note = Db.required(body.note(), 500, "o motivo");
                    if (note.length() < 3)
                        throw new BusinessRuleException("Explique o recebimento.");
                    var all =
                            db.rows(
                                    "SELECT * FROM purchase_item WHERE purchase_id=? ORDER BY"
                                            + " product_id",
                                    id);
                    var products = new HashMap<UUID, Map<String, Object>>();
                    for (var item : all)
                        products.put(
                                Db.id(item, "productId"),
                                catalog.lockProduct(Db.id(item, "productId")));
                    UUID receipt = UUID.randomUUID();
                    var received = new ArrayList<Object[]>();
                    BigDecimal total = BigDecimal.ZERO;
                    Set<UUID> seen = new HashSet<>();
                    for (var line : body.items()) {
                        if (!seen.add(line.itemId()))
                            throw new BusinessRuleException("Item repetido.");
                        var item =
                                all.stream()
                                        .filter(i -> Db.id(i, "id").equals(line.itemId()))
                                        .findFirst()
                                        .orElseThrow(
                                                () ->
                                                        new NotFoundException(
                                                                "Item não pertence ao pedido."));
                        var p = products.get(Db.id(item, "productId"));
                        if (!item.get("unitCode").equals(p.get("baseUnitCode")))
                            throw new ConflictException(
                                    "Unidade alterada desde o pedido. Confira o cadastro.");
                        catalog.quantity(p, line.quantity());
                        BigDecimal before = Db.decimal(item, "received"),
                                after = before.add(line.quantity());
                        if (after.compareTo(Db.decimal(item, "quantity")) > 0)
                            throw new ConflictException(
                                    "Quantidade recebida supera o restante do pedido.");
                        BigDecimal cost = Db.decimal(item, "unitCost");
                        total =
                                total.add(
                                        after.multiply(cost)
                                                .setScale(2, RoundingMode.HALF_UP)
                                                .subtract(
                                                        before.multiply(cost)
                                                                .setScale(
                                                                        2, RoundingMode.HALF_UP)));
                        var movement =
                                inventory.execute(
                                        actor,
                                        UUID.randomUUID(),
                                        new Command(
                                                Db.id(item, "productId"),
                                                Db.LOCATION,
                                                Kind.ENTRY,
                                                line.quantity(),
                                                ("Recebimento " + id + ": " + note)
                                                        .substring(
                                                                0,
                                                                Math.min(
                                                                        500,
                                                                        ("Recebimento "
                                                                                        + id
                                                                                        + ": "
                                                                                        + note)
                                                                                .length())),
                                                null,
                                                null));
                        db.jdbc.update(
                                "UPDATE purchase_item SET received=? WHERE id=?",
                                after,
                                line.itemId());
                        received.add(
                                new Object[] {
                                    UUID.randomUUID(),
                                    receipt,
                                    line.itemId(),
                                    line.quantity(),
                                    movement.id()
                                });
                    }
                    boolean full =
                            Boolean.TRUE.equals(
                                    db.jdbc.queryForObject(
                                            "SELECT bool_and(received=quantity) FROM purchase_item"
                                                    + " WHERE purchase_id=?",
                                            Boolean.class,
                                            id));
                    BigDecimal orderGoods = db.jdbc.queryForObject(
                            "SELECT COALESCE(sum(line_total),0) FROM purchase_item WHERE purchase_id=?",
                            BigDecimal.class, id);
                    BigDecimal cumulativeGoods = db.jdbc.queryForObject(
                            "SELECT COALESCE(sum(round(received*unit_cost,2)),0) FROM purchase_item WHERE purchase_id=?",
                            BigDecimal.class, id);
                    BigDecimal freightTotal = Db.decimal(order, "freightCost");
                    BigDecimal previouslyAllocated = db.jdbc.queryForObject(
                            "SELECT COALESCE(sum(freight_cost),0) FROM purchase_receipt WHERE purchase_id=? AND cancelled_at IS NULL",
                            BigDecimal.class, id);
                    BigDecimal cumulativeFreight = full
                            ? freightTotal
                            : orderGoods.signum() == 0
                                    ? BigDecimal.ZERO
                                    : freightTotal.multiply(cumulativeGoods)
                                            .divide(orderGoods, 2, RoundingMode.HALF_UP);
                    BigDecimal freightDelta = cumulativeFreight.subtract(previouslyAllocated);
                    if (freightDelta.signum() < 0)
                        throw new ConflictException("Não foi possível reconciliar o frete dos recebimentos.");
                    total = total.add(freightDelta);
                    db.jdbc.update(
                            "INSERT INTO purchase_receipt(id,purchase_id,total,freight_cost,actor_id,note)"
                                    + " VALUES(?,?,?,?,?,?)",
                            receipt,
                            id,
                            Db.money(total, false),
                            Db.money(freightDelta, false),
                            actor.userId(),
                            note);
                    for (var item : received)
                        db.jdbc.update(
                                "INSERT INTO"
                                    + " purchase_receipt_item(id,receipt_id,purchase_item_id,quantity,movement_id)"
                                    + " VALUES(?,?,?,?,?)",
                                item);
                    // Rateia o frete da remessa por valor dos itens e calcula o custo posto na loja.
                    var receiptLines = db.rows(
                            "SELECT ri.id,ri.movement_id,ri.quantity,pi.product_id,pi.unit_cost "
                                    + "FROM purchase_receipt_item ri JOIN purchase_item pi "
                                    + "ON pi.id=ri.purchase_item_id WHERE ri.receipt_id=? "
                                    + "ORDER BY pi.product_id", receipt);
                    BigDecimal receiptGoods = receiptLines.stream()
                            .map(line -> Db.decimal(line, "quantity")
                                    .multiply(Db.decimal(line, "unitCost"))
                                    .setScale(2, RoundingMode.HALF_UP))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal freightRemaining = freightDelta;
                    for (int index = 0; index < receiptLines.size(); index++) {
                        var line = receiptLines.get(index);
                        BigDecimal lineGoods = Db.decimal(line, "quantity")
                                .multiply(Db.decimal(line, "unitCost"))
                                .setScale(2, RoundingMode.HALF_UP);
                        BigDecimal freightShare = index == receiptLines.size() - 1
                                ? freightRemaining
                                : receiptGoods.signum() == 0 ? BigDecimal.ZERO
                                    : freightDelta.multiply(lineGoods)
                                        .divide(receiptGoods, 2, RoundingMode.HALF_UP)
                                        .min(freightRemaining);
                        freightRemaining = freightRemaining.subtract(freightShare);
                        BigDecimal quantity = Db.decimal(line, "quantity");
                        BigDecimal landedUnitCost = Db.decimal(line, "unitCost")
                                .add(freightShare.divide(quantity, 6, RoundingMode.HALF_UP));
                        db.jdbc.update("UPDATE purchase_receipt_item SET landed_unit_cost=? WHERE id=?",
                                landedUnitCost, Db.id(line, "id"));
                        costing.add(Db.id(line, "productId"), Db.id(line, "movementId"), receipt,
                                "PURCHASE_RECEIPT", quantity, landedUnitCost);
                    }
                    finance.createTitle(
                            actor,
                            Db.id(order, "supplierId"),
                            "PAYABLE",
                            "Recebimento do pedido " + id,
                            total,
                            LocalDate.parse(Db.text(order, "dueDate")),
                            null,
                            receipt);
                    db.jdbc.update(
                            "UPDATE purchase_order SET status=? WHERE id=?",
                            full ? "RECEIVED" : "PARTIAL",
                            id);
                    db.audit(actor, "PURCHASE_RECEIVED", id, note + "; valor recebido: " + total);
                    return receipt;
                });
        return detail(actor, id);
    }

    public Map<String, Object> returnReceipt(
            Actor actor, UUID key, UUID orderId, UUID receiptId, String reason) {
        gate.run(
                actor,
                key,
                "purchase:return:" + receiptId,
                reason,
                () -> {
                    db.one(
                            "SELECT id FROM purchase_order WHERE id=? AND store_id=? FOR UPDATE",
                            orderId,
                            actor.storeId());
                    var items =
                            db.rows(
                                    "SELECT * FROM purchase_item WHERE purchase_id=? ORDER BY"
                                            + " product_id",
                                    orderId);
                    for (var item : items) catalog.lockProduct(Db.id(item, "productId"));
                    var receipt =
                            db.one(
                                    "SELECT * FROM purchase_receipt WHERE id=? AND purchase_id=?"
                                            + " FOR UPDATE",
                                    receiptId,
                                    orderId);
                    if (receipt.get("cancelledAt") != null)
                        throw new ConflictException("Recebimento já devolvido.");
                    String note = Db.required(reason, 400, "o motivo");
                    if (note.length() < 3)
                        throw new BusinessRuleException("Informe o motivo da devolução.");
                    UUID returned = UUID.randomUUID();
                    db.jdbc.update(
                            "INSERT INTO purchase_return(id,receipt_id,actor_id,reason)"
                                    + " VALUES(?,?,?,?)",
                            returned,
                            receiptId,
                            actor.userId(),
                            note);
                    for (var line :
                            db.rows(
                                    "SELECT r.*,p.product_id FROM purchase_receipt_item r JOIN"
                                            + " purchase_item p ON p.id=r.purchase_item_id WHERE"
                                            + " r.receipt_id=? ORDER BY p.product_id",
                                    receiptId)) {
                        var movement =
                                inventory.execute(
                                        actor,
                                        UUID.randomUUID(),
                                        new Command(
                                                Db.id(line, "productId"),
                                                Db.LOCATION,
                                                Kind.REVERSAL,
                                                Db.decimal(line, "quantity"),
                                                "Devolução ao fornecedor "
                                                        + receiptId
                                                        + ": "
                                                        + note,
                                                null,
                                                Db.id(line, "movementId")));
                        db.jdbc.update(
                                "UPDATE purchase_item SET received=received-? WHERE id=?",
                                Db.decimal(line, "quantity"),
                                Db.id(line, "purchaseItemId"));
                        costing.remove(Db.id(line, "productId"), movement.id(), receiptId,
                                "PURCHASE_RETURN", Db.decimal(line, "quantity"),
                                Db.decimal(line, "landedUnitCost"));
                        db.jdbc.update(
                                "INSERT INTO"
                                    + " purchase_return_item(id,return_id,receipt_item_id,movement_id)"
                                    + " VALUES(?,?,?,?)",
                                UUID.randomUUID(),
                                returned,
                                Db.id(line, "id"),
                                movement.id());
                    }
                    finance.cancelReceipt(actor, receiptId, note);
                    db.jdbc.update(
                            "UPDATE purchase_receipt SET cancelled_at=now(),cancellation_reason=?"
                                    + " WHERE id=?",
                            note,
                            receiptId);
                    boolean empty =
                            Boolean.TRUE.equals(
                                    db.jdbc.queryForObject(
                                            "SELECT bool_and(received=0) FROM purchase_item WHERE"
                                                    + " purchase_id=?",
                                            Boolean.class,
                                            orderId));
                    db.jdbc.update(
                            "UPDATE purchase_order SET status=? WHERE id=?",
                            empty ? "OPEN" : "PARTIAL",
                            orderId);
                    db.audit(
                            actor,
                            "PURCHASE_RETURNED",
                            orderId,
                            note + "; recebimento " + receiptId);
                    return returned;
                });
        return detail(actor, orderId);
    }

    public Map<String, Object> cancel(Actor actor, UUID key, UUID id, String reason) {
        gate.run(
                actor,
                key,
                "purchase:cancel:" + id,
                reason,
                () -> {
                    var order =
                            db.one(
                                    "SELECT status FROM purchase_order WHERE id=? AND store_id=?"
                                            + " FOR UPDATE",
                                    id,
                                    actor.storeId());
                    if (!order.get("status").equals("OPEN"))
                        throw new ConflictException(
                                "Somente pedidos sem recebimentos podem ser cancelados.");
                    db.jdbc.update("UPDATE purchase_order SET status='CANCELLED' WHERE id=?", id);
                    db.audit(actor, "PURCHASE_CANCELLED", id, Db.required(reason, 500, "o motivo"));
                    return id;
                });
        return detail(actor, id);
    }
}
