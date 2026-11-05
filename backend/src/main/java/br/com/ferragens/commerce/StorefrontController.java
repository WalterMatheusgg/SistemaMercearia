package br.com.ferragens.commerce;

import br.com.ferragens.auth.*;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@ConditionalOnProperty(name = "app.customer-portal-enabled", havingValue = "true")
@RequestMapping("/api")
public class StorefrontController {
    private final CatalogService catalog;
    private final SalesService sales;
    private final CurrentActorService actors;

    public StorefrontController(
            CatalogService catalog, SalesService sales, CurrentActorService actors) {
        this.catalog = catalog;
        this.sales = sales;
        this.actors = actors;
    }

    @GetMapping("/catalog")
    public Map<String, Object> catalog(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page) {
        return catalog.catalog(query, page);
    }

    @PostMapping("/catalog/preview")
    public Map<String, Object> preview(@Valid @RequestBody SalesService.Checkout body) {
        return sales.checkoutPreview(body);
    }

    @GetMapping("/catalog/store")
    public Map<String, Object> store() {
        return catalog.settings();
    }

    @PostMapping("/account/orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Map<String, Object> checkout(
            Authentication auth,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody SalesService.Checkout body) {
        return sales.checkout(actors.require(auth), key, body);
    }

    @GetMapping("/account/orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<Map<String, Object>> orders(Authentication auth) {
        return sales.customerOrders(actors.require(auth));
    }

    @GetMapping("/account/orders/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Map<String, Object> order(Authentication auth, @PathVariable UUID id) {
        return sales.customerDetail(actors.require(auth), id);
    }

    @PostMapping("/account/orders/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Map<String, Object> cancel(
            Authentication auth,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody CommerceController.Reason body) {
        return sales.cancel(actors.require(auth), key, id, body.reason(), true);
    }
}
