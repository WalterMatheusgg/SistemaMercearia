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

    @GetMapping("/balances")
    public Page<Balance> balances(
            Authentication authentication,
            @RequestParam UUID locationId,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "false") boolean onlyLow,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return inventory.balances(
                actors.require(authentication),
                locationId,
                query,
                onlyLow,
                includeInactive,
                page,
                size);
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
