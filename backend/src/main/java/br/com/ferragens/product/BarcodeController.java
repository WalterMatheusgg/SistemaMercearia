package br.com.ferragens.product;

import br.com.ferragens.auth.CurrentActorService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Consultas autenticadas para leitores USB/Bluetooth que digitam e enviam Enter. */
@RestController
@RequestMapping("/api/admin/barcodes")
@PreAuthorize("hasAnyRole('OWNER','MANAGER','STOCKKEEPER','SELLER','CASHIER')")
public class BarcodeController {
    private final BarcodeCatalogService barcodes;
    private final CurrentActorService actors;

    public BarcodeController(BarcodeCatalogService barcodes, CurrentActorService actors) {
        this.barcodes = barcodes;
        this.actors = actors;
    }

    @GetMapping("/lookup")
    public BarcodeCatalogService.Item lookup(Authentication authentication,
            @RequestParam String code, @RequestParam(required = false) UUID locationId) {
        return barcodes.byBarcode(actors.require(authentication), locationId, code);
    }

    @GetMapping("/search")
    public List<BarcodeCatalogService.Item> search(Authentication authentication,
            @RequestParam String query, @RequestParam(required = false) UUID locationId) {
        return barcodes.search(actors.require(authentication), locationId, query);
    }

    @GetMapping("/product/{id}")
    public BarcodeCatalogService.Item product(Authentication authentication,
            @PathVariable UUID id, @RequestParam(required = false) UUID locationId) {
        return barcodes.byProduct(actors.require(authentication), locationId, id);
    }
}
