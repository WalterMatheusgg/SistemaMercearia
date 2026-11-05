package br.com.ferragens.inventory;

import static br.com.ferragens.inventory.InventoryModels.*;

import br.com.ferragens.auth.CurrentActorService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/inventory")
@PreAuthorize("hasAnyRole('OWNER','MANAGER','STOCKKEEPER','SELLER','CASHIER')")
public class InventoryController {
    private final InventoryService inventory;
    private final CurrentActorService actors;
    private final br.com.ferragens.commerce.Db db;

    public InventoryController(
            InventoryService inventory,
            CurrentActorService actors,
            br.com.ferragens.commerce.Db db) {
        this.db = db;
        this.inventory = inventory;
        this.actors = actors;
    }

    @GetMapping("/locations")
    public List<Location> locations(Authentication authentication) {
        return inventory.locations(actors.require(authentication));
    }

    public record CategoryChoice(UUID id, String name) {}

    @GetMapping("/categories")
    public List<CategoryChoice> categories() {
        return db.jdbc.query("SELECT id,name FROM category WHERE active ORDER BY name",
                (rs, row) -> new CategoryChoice(
                        rs.getObject("id", UUID.class), rs.getString("name")));
    }

    @GetMapping("/balances")
    public Page<Balance> balances(
            Authentication authentication,
            @RequestParam UUID locationId,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "false") boolean onlyLow,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID categoryId) {
        return inventory.balances(
                actors.require(authentication),
                locationId,
                query,
                onlyLow,
                includeInactive,
                page,
                size,
                categoryId);
    }

    /**
     * Sugestões somente de leitura: quando o disponível chega ao mínimo,
     * propõe recompor até duas vezes o mínimo já configurado pela loja.
     * A loja sempre confirma fornecedor, preço, quantidade e pedido.
     */
    @GetMapping("/replenishment")
    public List<java.util.Map<String, Object>> replenishment(
            Authentication authentication,
            @RequestParam UUID locationId,
            @RequestParam(required = false) UUID categoryId) {
        var actor = actors.require(authentication);
        // Não usar recomendação como autorização: validar o local da loja.
        inventory.locations(actor).stream()
                .filter(location -> location.id().equals(locationId))
                .findFirst().orElseThrow(() ->
                        new br.com.ferragens.shared.NotFoundException("Local de estoque não encontrado."));
        String sql = """
            SELECT p.id AS product_id, p.name, p.brand, c.name AS category_name,
                u.code AS unit,
                b.minimum_quantity,
                (b.physical-b.reserved-b.unavailable) AS available,
                (ceil(greatest(2*b.minimum_quantity-
                      (b.physical-b.reserved-b.unavailable),0)/u.quantity_step)
                      *u.quantity_step)::numeric(19,4) AS suggested_quantity,
                last_order.supplier_id, last_order.supplier_name,
                last_order.unit_cost AS last_unit_cost
            FROM inventory_balance b
            JOIN product p ON p.id=b.product_id
            JOIN category c ON c.id=p.category_id
            JOIN unit u ON u.code=p.base_unit_code
            LEFT JOIN LATERAL (
                SELECT o.supplier_id, o.supplier_name, i.unit_cost
                FROM purchase_item i JOIN purchase_order o ON o.id=i.purchase_id
                WHERE i.product_id=p.id AND o.store_id=? AND o.status<>'CANCELLED'
                ORDER BY o.created_at DESC,o.id DESC LIMIT 1
            ) last_order ON true
            WHERE b.location_id=? AND p.active AND b.minimum_quantity>0
              AND b.physical-b.reserved-b.unavailable<=b.minimum_quantity
            """;
        if (categoryId != null) {
            sql += " AND p.category_id=? ";
            return db.rows(sql + " ORDER BY b.physical-b.reserved-b.unavailable, p.name LIMIT 30",
                    actor.storeId(), locationId, categoryId);
        }
        return db.rows(sql + " ORDER BY b.physical-b.reserved-b.unavailable, p.name LIMIT 30",
                actor.storeId(), locationId);
    }

    @GetMapping("/movements")
    public Page<Movement> history(
            Authentication authentication,
            @RequestParam UUID locationId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) Kind kind,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return inventory.history(
                actors.require(authentication), locationId, productId, kind, page, size);
    }

    @PostMapping("/movements")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STOCKKEEPER')")
    public Movement execute(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody Command command) {
        if ((command.kind() == Kind.COUNT || command.kind() == Kind.REVERSAL)
                && authentication.getAuthorities().stream()
                        .noneMatch(
                                a ->
                                        a.getAuthority().equals("ROLE_OWNER")
                                                || a.getAuthority().equals("ROLE_MANAGER"))) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Ajustes e estornos exigem autorização da gerência.");
        }
        if (command.kind() == Kind.REVERSAL
                && Boolean.TRUE.equals(
                        db.jdbc.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM sale_item WHERE movement_id=?) OR"
                                        + " EXISTS(SELECT 1 FROM purchase_receipt_item WHERE"
                                        + " movement_id=?)",
                                Boolean.class,
                                command.reversesMovementId(),
                                command.reversesMovementId())))
            throw new br.com.ferragens.shared.ConflictException(
                    "Use o cancelamento/devolução da venda para corrigir movimento comercial."
                            + " Recebimentos precisam de devolução ao fornecedor.");
        return inventory.execute(actors.require(authentication), key, command);
    }
}
