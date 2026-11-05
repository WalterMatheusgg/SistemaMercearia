package br.com.ferragens.commerce;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Consulta para vendas e compras internas, sem publicar a vitrine ao público. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('OWNER','MANAGER','SELLER','CASHIER','STOCKKEEPER','FINANCE')")
public class InternalCatalogController {
    private final CatalogService catalog;

    public InternalCatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/catalog")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page) {
        return catalog.catalog(query, page);
    }
}
