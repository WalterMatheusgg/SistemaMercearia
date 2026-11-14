package br.com.ferragens.commerce;

import br.com.ferragens.auth.*;
import br.com.ferragens.auth.CurrentActorService.Actor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('OWNER','MANAGER','SELLER','CASHIER','STOCKKEEPER','FINANCE')")
public class CommerceController {
    private final CurrentActorService actors;
    private final ContactsService contacts;
    private final SalesService sales;
    private final PurchasingService purchases;
    private final CashService cash;
    private final FinanceService finance;
    private final ReportingService reports;
    private final StoreWorkbookService workbooks;

    public CommerceController(
            CurrentActorService actors,
            ContactsService contacts,
            SalesService sales,
            PurchasingService purchases,
            CashService cash,
            FinanceService finance,
            ReportingService reports,
            StoreWorkbookService workbooks) {
        this.actors = actors;
        this.contacts = contacts;
        this.sales = sales;
        this.purchases = purchases;
        this.cash = cash;
        this.finance = finance;
        this.reports = reports;
        this.workbooks = workbooks;
    }

    public record Reason(@NotBlank @Size(min = 3, max = 500) String reason) {}

    public record Status(@NotBlank String status) {}

    @GetMapping("/contacts/{kind}")
    public Map<String, Object> contacts(
            Authentication auth,
            @PathVariable String kind,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        contactRole(auth, kind);
        return contacts.list(actor(auth), kind, query, page, size);
    }

    @GetMapping("/contacts/{kind}/{id}")
    public Map<String, Object> contact(
            Authentication auth, @PathVariable String kind, @PathVariable UUID id) {
        contactRole(auth, kind);
        return contactView(auth, contacts.detail(actor(auth), id, kind));
    }

    @PostMapping("/contacts/{kind}")
    public Map<String, Object> createContact(
            Authentication auth,
            @PathVariable String kind,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody ContactsService.Contact body) {
        contactRole(auth, kind);
        return contactView(auth, contacts.save(actor(auth), key, kind, null, body));
    }

    @PutMapping("/contacts/{kind}/{id}")
    public Map<String, Object> updateContact(
            Authentication auth,
            @PathVariable String kind,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody ContactsService.Contact body) {
        contactRole(auth, kind);
        return contactView(auth, contacts.save(actor(auth), key, kind, id, body));
    }

    @GetMapping("/sales")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','SELLER','CASHIER')")
    public Map<String, Object> sales(
            Authentication auth,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page) {
        return sales.list(actor(auth), status, page);
    }

    @GetMapping("/sales/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','SELLER','CASHIER')")
    public Map<String, Object> sale(Authentication auth, @PathVariable UUID id) {
        return sales.detail(actor(auth), id);
    }

    @PostMapping("/sales/change")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','SELLER','CASHIER')")
    public Map<String, Object> change(@Valid @RequestBody SalesService.CashCalculation body) {
        return sales.change(body);
    }

    @PostMapping("/sales/preview")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','SELLER','CASHIER')")
    public Map<String, Object> preview(@RequestBody SalesService.Order body) {
        return sales.preview(body);
    }

    @PostMapping("/sales")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','SELLER','CASHIER')")
    public Map<String, Object> sale(
            Authentication auth,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody SalesService.Order body) {
        if (body.discount() != null
                && body.discount().signum() > 0
                && !has(auth, "OWNER", "MANAGER"))
            throw new org.springframework.security.access.AccessDeniedException(
                    "Descontos exigem gerência.");
        return sales.create(actor(auth), key, body);
    }

    @PostMapping("/sales/{id}/confirm")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','SELLER','CASHIER')")
    public Map<String, Object> confirm(
            Authentication auth,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody SalesService.Confirm body) {
        return sales.confirm(actor(auth), key, id, body);
    }

    @PostMapping("/sales/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public Map<String, Object> cancel(
            Authentication auth,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody Reason body) {
        return sales.cancel(actor(auth), key, id, body.reason(), false);
    }

    @PostMapping("/sales/{id}/fulfillment")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','SELLER','CASHIER')")
    public Map<String, Object> status(
            Authentication auth,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody Status body) {
        return sales.fulfillment(actor(auth), key, id, body.status());
    }

    @GetMapping("/purchases")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STOCKKEEPER')")
    public Map<String, Object> purchases(
            Authentication auth, @RequestParam(defaultValue = "0") int page) {
        return purchases.list(actor(auth), page);
    }

    @GetMapping("/purchases/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STOCKKEEPER')")
    public Map<String, Object> purchase(Authentication auth, @PathVariable UUID id) {
        return purchases.detail(actor(auth), id);
    }

    @PostMapping("/purchases")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public Map<String, Object> purchase(
            Authentication auth,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody PurchasingService.Order body) {
        return purchases.create(actor(auth), key, body);
    }

    @PostMapping("/purchases/{id}/receive")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STOCKKEEPER')")
    public Map<String, Object> receive(
            Authentication auth,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody PurchasingService.Receipt body) {
        return purchases.receive(actor(auth), key, id, body);
    }

    @PostMapping("/purchases/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public Map<String, Object> cancelPurchase(
            Authentication auth,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody Reason body) {
        return purchases.cancel(actor(auth), key, id, body.reason());
    }

    @PostMapping("/purchases/{id}/receipts/{receipt}/return")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public Map<String, Object> returnReceipt(
            Authentication auth,
            @PathVariable UUID id,
            @PathVariable UUID receipt,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody Reason body) {
        return purchases.returnReceipt(actor(auth), key, id, receipt, body.reason());
    }

    @GetMapping("/cash")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER','SELLER')")
    public Map<String, Object> cash(Authentication auth) {
        return cash.overview(actor(auth));
    }

    @GetMapping("/cash/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER','SELLER')")
    public Map<String, Object> cash(Authentication auth, @PathVariable UUID id) {
        return cash.detail(actor(auth), id);
    }

    @PostMapping("/cash/open")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public Map<String, Object> open(
            Authentication auth,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody CashService.Open body) {
        return cash.open(actor(auth), key, body);
    }

    @PostMapping("/cash/adjust")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public Map<String, Object> adjust(
            Authentication auth,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody CashService.Adjustment body) {
        return cash.adjust(actor(auth), key, body);
    }

    @PostMapping("/cash/{id}/close")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public Map<String, Object> close(
            Authentication auth,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody CashService.Close body) {
        return cash.close(actor(auth), key, id, body);
    }

    @GetMapping("/finance")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','FINANCE')")
    public Map<String, Object> finance(
            Authentication auth,
            @RequestParam(defaultValue = "") String direction,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page) {
        return finance.list(actor(auth), direction, status, page);
    }

    @GetMapping("/finance/summary")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','FINANCE')")
    public Map<String, Object> financeSummary(Authentication auth) {
        return finance.summary(actor(auth));
    }

    @GetMapping("/finance/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','FINANCE')")
    public Map<String, Object> account(Authentication auth, @PathVariable UUID id) {
        return finance.detail(actor(auth), id);
    }

    @PostMapping("/finance")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','FINANCE')")
    public Map<String, Object> account(
            Authentication auth,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody FinanceService.Account body) {
        return finance.create(actor(auth), key, body);
    }

    @PostMapping("/finance/{id}/settle")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','FINANCE')")
    public Map<String, Object> settle(
            Authentication auth,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody FinanceService.Payment body) {
        return finance.settle(actor(auth), key, id, body);
    }

    @PostMapping("/finance/payments/{id}/reverse")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','FINANCE')")
    public Map<String, Object> reverse(
            Authentication auth,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody Reason body) {
        return finance.reverse(actor(auth), key, id, body.reason());
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','FINANCE')")
    public Map<String, Object> report(
            Authentication auth,
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end) {
        return reports.report(actor(auth), start, end);
    }

    @GetMapping(value = "/reports.xlsx", produces = StoreWorkbookService.CONTENT_TYPE)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','FINANCE')")
    public ResponseEntity<byte[]> excel(
            Authentication auth,
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end) {
        LocalDate from = start == null ? Db.today().withDayOfMonth(1) : start;
        LocalDate until = end == null ? Db.today() : end;
        byte[] bytes = workbooks.export(actor(auth), from, until);
        String filename = "relatorio-loja-" + from + "_a_" + until + ".xlsx";
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(StoreWorkbookService.CONTENT_TYPE))
                .contentLength(bytes.length)
                .body(bytes);
    }

    @GetMapping(value = "/reports.csv", produces = "text/csv")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','FINANCE')")
    public ResponseEntity<String> csv(
            Authentication auth,
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=relatorio-vendas.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(reports.csv(actor(auth), start, end));
    }

    private Map<String, Object> contactView(Authentication auth, Map<String, Object> result) {
        if (!has(auth, "OWNER", "MANAGER", "SELLER", "CASHIER", "FINANCE"))
            result.remove("accounts");
        return result;
    }

    private Actor actor(Authentication auth) {
        return actors.require(auth);
    }

    private boolean has(Authentication auth, String... roles) {
        return auth.getAuthorities().stream()
                .anyMatch(
                        a -> Arrays.asList(roles).contains(a.getAuthority().replace("ROLE_", "")));
    }

    private void contactRole(Authentication auth, String kind) {
        if (kind.equals("SUPPLIER")
                ? !has(auth, "OWNER", "MANAGER", "STOCKKEEPER", "FINANCE")
                : !has(auth, "OWNER", "MANAGER", "SELLER", "CASHIER", "FINANCE"))
            throw new org.springframework.security.access.AccessDeniedException(
                    "Cadastro não autorizado para este papel.");
    }
}
