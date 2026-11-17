package br.com.ferragens;

import static org.assertj.core.api.Assertions.*;

import br.com.ferragens.auth.*;
import br.com.ferragens.auth.CurrentActorService.Actor;
import br.com.ferragens.commerce.*;
import br.com.ferragens.inventory.*;
import br.com.ferragens.inventory.InventoryModels.*;
import br.com.ferragens.notification.OutboxWorker;
import br.com.ferragens.shared.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.*;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CommerceIntegrationTest {
    private static final String SCHEMA =
            "commerce_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        IntegrationServices.configure(p, SCHEMA);
    }

    @Autowired Db db;
    @Autowired InventoryService stock;
    @Autowired SalesService sales;
    @Autowired PurchasingService purchases;
    @Autowired SupplierOffersService supplierOffers;
    @Autowired FinanceService finance;
    @Autowired CashService cash;
    @Autowired CatalogService catalog;
    @Autowired ReportingService reports;
    @Autowired StoreWorkbookService workbooks;
    @Autowired AccountsService accounts;
    @Autowired JavaMailSender mail;
    Actor actor, customerActor;
    UUID product, customer, supplier;

    @BeforeEach
    void fixtures() {
        UUID user = UUID.randomUUID();
        String username = "owner-" + user;
        db.jdbc.update(
                "INSERT INTO app_user(id,store_id,username,password_hash,email,email_verified)"
                        + " VALUES(?,?,?,?,?,true)",
                user,
                Db.STORE,
                username,
                "unused",
                user + "@ferragens.local");
        db.jdbc.update("INSERT INTO user_role(user_id,role_code) VALUES(?,'ROLE_OWNER')", user);
        actor = new Actor(user, Db.STORE, username);
        UUID buyer = UUID.randomUUID();
        db.jdbc.update(
                "INSERT INTO app_user(id,store_id,username,password_hash,email,email_verified)"
                        + " VALUES(?,?,?,?,?,true)",
                buyer,
                Db.STORE,
                "customer-" + buyer,
                "unused",
                buyer + "@ferragens.local");
        db.jdbc.update("INSERT INTO user_role(user_id,role_code) VALUES(?,'ROLE_CUSTOMER')", buyer);
        customerActor = new Actor(buyer, Db.STORE, "customer-" + buyer);
        customer = UUID.randomUUID();
        supplier = UUID.randomUUID();
        db.jdbc.update(
                "INSERT INTO party(id,store_id,kind,name,user_id) VALUES(?,?,'CUSTOMER','Cliente"
                        + " identificado',?)",
                customer,
                Db.STORE,
                buyer);
        db.jdbc.update(
                "INSERT INTO party(id,store_id,kind,name) VALUES(?,?,'SUPPLIER','Fornecedor de"
                        + " teste')",
                supplier,
                Db.STORE);
        product = UUID.randomUUID();
        db.jdbc.update(
                "INSERT INTO product(id,code,name,category_id,base_unit_code,cost,price)"
                        + " VALUES(?,?,?,?,'UN',1,2)",
                product,
                "C-" + product.toString().substring(0, 16),
                "Material " + product,
                UUID.fromString("00000000-0000-0000-0000-000000000104"));
        // A store has one physical register; close prior test registers without removing financial
        // history.
        db.jdbc.update(
                "UPDATE cash_session SET closed_at=now(),counted_amount=opening_amount WHERE"
                        + " store_id=? AND closed_at IS NULL",
                Db.STORE);
    }

    @Test
    void excelReportContainsSeparateManagementSheetsAndFiltersByDate() throws Exception {
        opening("3");
        byte[] content = workbooks.export(actor, Db.today(), Db.today());
        assertThat(content).startsWith((byte) 'P', (byte) 'K');
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertThat(book.getNumberOfSheets()).isEqualTo(10);
            assertThat(book.getSheet("Resumo")).isNotNull();
            assertThat(book.getSheet("Vendas")).isNotNull();
            assertThat(book.getSheet("Itens vendidos")).isNotNull();
            assertThat(book.getSheet("Entradas e saídas")).isNotNull();
            assertThat(book.getSheet("Estoque atual")).isNotNull();
            assertThat(book.getSheet("Pagamentos")).isNotNull();
            assertThat(book.getSheet("Contas")).isNotNull();
            assertThat(book.getSheet("Movimentos de caixa")).isNotNull();
            assertThat(book.getSheet("Compras")).isNotNull();
            assertThat(book.getSheet("Receb. compras")).isNotNull();
            assertThat(book.getSheet("Compras").getRow(3).getCell(4).getStringCellValue())
                    .isEqualTo("Frete do pedido");
            assertThat(book.getSheet("Receb. compras").getRow(3).getCell(3).getStringCellValue())
                    .isEqualTo("Frete alocado");
            var movementSheet = book.getSheet("Entradas e saídas");
            assertThat(movementSheet.getRow(3).getCell(7).getStringCellValue()).isEqualTo("Variação (+/-)");
            assertThat(movementSheet.getLastRowNum()).isGreaterThanOrEqualTo(4);
            assertThat(book.getSheet("Estoque atual").getRow(3).getCell(9).getStringCellValue())
                    .isEqualTo("Disponível");
        }
        assertThatThrownBy(() -> workbooks.export(actor, Db.today(), Db.today().minusDays(1)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void posSaleRetryAndFullReturnReconcileStockDrawerAndAccount() {
        opening("50");
        cash.open(actor, UUID.randomUUID(), new CashService.Open(m("50")));
        UUID key = UUID.randomUUID();
        var body = order("15", List.of(payment("30", "CASH")), false);
        var sale = sales.create(actor, key, body);
        UUID id = Db.id(sale, "id");
        assertThat(sales.create(actor, key, body).get("id")).isEqualTo(id.toString());
        assertThat(balance("physical")).isEqualByComparingTo("35");
        assertThat(drawer()).isEqualByComparingTo("80");
        var cancelled =
                sales.cancel(actor, UUID.randomUUID(), id, "Cliente devolveu toda a compra", false);
        assertThat(cancelled.get("status")).isEqualTo("CANCELLED");
        assertThat(balance("physical")).isEqualByComparingTo("50");
        assertThat(drawer()).isEqualByComparingTo("50");
        var title = db.one("SELECT * FROM financial_account WHERE sale_id=?", id);
        assertThat(title.get("status")).isEqualTo("CANCELLED");
        assertThat(Db.decimal(title, "paid")).isZero();
        assertThat(
                        db.jdbc.queryForObject(
                                "SELECT count(*) FROM sale_item WHERE product_id=?",
                                Long.class,
                                product))
                .isEqualTo(1);
        assertThatThrownBy(
                        () ->
                                sales.cancel(
                                        actor, UUID.randomUUID(), id, "Repetição indevida", false))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void creditPaymentDoesNotCreateAnotherSale() {
        opening("50");
        cash.open(actor, UUID.randomUUID(), new CashService.Open(m("0")));
        UUID id = Db.id(sales.create(actor, UUID.randomUUID(), order("5", List.of(), false)), "id");
        UUID title = Db.id(db.one("SELECT id FROM financial_account WHERE sale_id=?", id), "id");
        UUID key = UUID.randomUUID();
        finance.settle(actor, key, title, payment("4", "PIX"));
        finance.settle(actor, key, title, payment("4", "PIX"));
        assertThat(Db.decimal(finance.detail(actor, title), "paid")).isEqualByComparingTo("4");
        assertThat(
                        db.jdbc.queryForObject(
                                "SELECT count(*) FROM sale_item WHERE product_id=?",
                                Long.class,
                                product))
                .isEqualTo(1);
        assertThat(drawer()).isZero();
        assertThat(sales.customerOrders(customerActor))
                .anyMatch(s -> s.get("id").equals(id.toString()));
    }

    @Test
    void insufficientSaleRollsBackDocumentTitleAndStock() {
        opening("2");
        cash.open(actor, UUID.randomUUID(), new CashService.Open(m("0")));
        assertThatThrownBy(
                        () ->
                                sales.create(
                                        actor,
                                        UUID.randomUUID(),
                                        order("3", List.of(payment("6", "PIX")), false)))
                .isInstanceOf(ConflictException.class);
        assertThat(balance("physical")).isEqualByComparingTo("2");
        assertThat(
                        db.jdbc.queryForObject(
                                "SELECT count(*) FROM sale_item WHERE product_id=?",
                                Long.class,
                                product))
                .isZero();
    }

    @Test
    void concurrentPosSalesCannotOversell() throws Exception {
        opening("10");
        cash.open(actor, UUID.randomUUID(), new CashService.Open(m("0")));
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        Callable<Boolean> task =
                () -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException();
                    try {
                        sales.create(
                                actor,
                                UUID.randomUUID(),
                                order("7", List.of(payment("14", "PIX")), false));
                        return true;
                    } catch (ConflictException expected) {
                        return false;
                    }
                };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(task);
            var b = executor.submit(task);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(
                            (a.get(20, TimeUnit.SECONDS) ? 1 : 0)
                                    + (b.get(20, TimeUnit.SECONDS) ? 1 : 0))
                    .isEqualTo(1);
        }
        assertThat(balance("physical")).isEqualByComparingTo("3");
    }

    @Test
    void quotePreservesPriceAndOnlyConsumesStockWhenConfirmed() {
        opening("50");
        UUID id = Db.id(sales.create(actor, UUID.randomUUID(), order("5", List.of(), true)), "id");
        assertThat(balance("physical")).isEqualByComparingTo("50");
        assertThat(db.rows("SELECT id FROM financial_account WHERE sale_id=?", id)).isEmpty();
        db.jdbc.update("UPDATE product SET price=10 WHERE id=?", product);
        cash.open(actor, UUID.randomUUID(), new CashService.Open(m("0")));
        var result =
                sales.confirm(
                        actor,
                        UUID.randomUUID(),
                        id,
                        new SalesService.Confirm(List.of(payment("10", "CASH")), Db.today()));
        assertThat(result.get("total")).isEqualTo("10.00");
        assertThat(balance("physical")).isEqualByComparingTo("45");
        assertThat(drawer()).isEqualByComparingTo("10");
    }

    @Test
    void checkoutReservesOnceAndCustomerCancelReleases() {
        opening("50");
        UUID key = UUID.randomUUID();
        var body = checkout("15");
        UUID id = Db.id(sales.checkout(customerActor, key, body), "id");
        sales.checkout(customerActor, key, body);
        assertThat(balance("physical")).isEqualByComparingTo("50");
        assertThat(balance("reserved")).isEqualByComparingTo("15");
        assertThat(catalog.catalog(productCode(), 0).get("content").toString())
                .doesNotContain("cost", "unitCost");
        sales.cancel(customerActor, UUID.randomUUID(), id, "Não preciso mais", true);
        assertThat(balance("reserved")).isZero();
    }

    @Test
    void onlinePaymentConsumesReservationAndFulfillmentHasOrderedStates() {
        opening("50");
        UUID id = Db.id(sales.checkout(customerActor, UUID.randomUUID(), checkout("15")), "id");
        var sale =
                sales.confirm(
                        actor,
                        UUID.randomUUID(),
                        id,
                        new SalesService.Confirm(List.of(payment("30", "PIX")), null));
        assertThat(sale.get("status")).isEqualTo("PAID");
        assertThat(balance("physical")).isEqualByComparingTo("35");
        assertThat(balance("reserved")).isZero();
        assertThatThrownBy(() -> sales.fulfillment(actor, UUID.randomUUID(), id, "COMPLETED"))
                .isInstanceOf(ConflictException.class);
        sales.fulfillment(actor, UUID.randomUUID(), id, "READY");
        assertThat(sales.fulfillment(actor, UUID.randomUUID(), id, "COMPLETED").get("status"))
                .isEqualTo("COMPLETED");
    }

    @Test
    void expiredReservationIsReleasedAndCannotBePaid() {
        opening("50");
        UUID id = Db.id(sales.checkout(customerActor, UUID.randomUUID(), checkout("15")), "id");
        db.jdbc.update("UPDATE sale SET expires_at=now()-interval '1 minute' WHERE id=?", id);
        assertThatThrownBy(
                        () ->
                                sales.confirm(
                                        actor,
                                        UUID.randomUUID(),
                                        id,
                                        new SalesService.Confirm(
                                                List.of(payment("30", "PIX")), null)))
                .isInstanceOf(ConflictException.class);
        sales.expire(id);
        sales.expire(id);
        assertThat(balance("reserved")).isZero();
        assertThat(sales.customerDetail(customerActor, id).get("status")).isEqualTo("EXPIRED");
    }

    @Test
    void customerCannotReadOrCancelSomeoneElsesOrder() {
        opening("50");
        UUID id = Db.id(sales.checkout(customerActor, UUID.randomUUID(), checkout("1")), "id");
        assertThatThrownBy(() -> sales.customerDetail(actor, id))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> sales.cancel(actor, UUID.randomUUID(), id, "Outro cliente", true))
                .isInstanceOf(NotFoundException.class);
        assertThat(balance("reserved")).isEqualByComparingTo("1");
    }

    @Test
    void supplierOfferComparisonAppliesQuantityTierMinimumAndFreight() {
        var offer = supplierOffers.save(actor, UUID.randomUUID(), null,
                new SupplierOffersService.Offer(
                        supplier, product, "Marca teste", "SKU-FORN-1", m("10.00"),
                        m("2"), Db.today(), Db.today().plusDays(30), 4, m("5.00"),
                        "Cotação válida para teste", true,
                        List.of(new SupplierOffersService.Tier(m("10"), m("8.00")))));
        UUID offerId = Db.id(offer, "id");
        var comparison = supplierOffers.compare(actor, product, m("10"));
        var rows = (List<Map<String,Object>>) comparison.get("content");
        assertThat(rows).hasSize(1);
        assertThat(Db.decimal(rows.getFirst(), "effectiveUnitCost")).isEqualByComparingTo("8.00");
        assertThat(Db.decimal(rows.getFirst(), "estimatedTotal")).isEqualByComparingTo("85.00");
        var belowMinimum = (List<?>) supplierOffers.compare(actor, product, m("1")).get("content");
        assertThat(belowMinimum).isEmpty();
        supplierOffers.deactivate(actor, UUID.randomUUID(), offerId);
        var afterDeactivate = (List<?>) supplierOffers.compare(actor, product, m("10")).get("content");
        assertThat(afterDeactivate).isEmpty();
    }

    @Test
    void revisingSupplierOfferPreservesPreviousPriceAndQuantityTiers() {
        var first = supplierOffers.save(actor, UUID.randomUUID(), null,
                new SupplierOffersService.Offer(
                        supplier, product, "Marca", null, m("10.00"), m("1"),
                        Db.today(), null, null, m("0"), "Cotação inicial", true,
                        List.of(new SupplierOffersService.Tier(m("5"), m("9.00")))));
        UUID firstId = Db.id(first, "id");
        var revised = supplierOffers.save(actor, UUID.randomUUID(), firstId,
                new SupplierOffersService.Offer(
                        supplier, product, "Marca", null, m("8.50"), m("1"),
                        Db.today(), null, null, m("0"), "Nova cotação", true, List.of()));
        UUID revisedId = Db.id(revised, "id");
        assertThat(revisedId).isNotEqualTo(firstId);
        var historical = supplierOffers.detail(actor, firstId);
        assertThat(historical.get("active")).isEqualTo(false);
        assertThat(Db.decimal(historical, "unitCost")).isEqualByComparingTo("10.00");
        assertThat((List<?>) historical.get("tiers")).hasSize(1);
        assertThat(Db.decimal(supplierOffers.detail(actor, revisedId), "unitCost"))
                .isEqualByComparingTo("8.50");
    }

    @Test
    void supplierOfferRejectsTierBelowMinimumOrderQuantity() {
        assertThatThrownBy(() -> supplierOffers.save(actor, UUID.randomUUID(), null,
                new SupplierOffersService.Offer(
                        supplier, product, null, null, m("10.00"), m("5"), Db.today(),
                        null, null, m("0"), null, true,
                        List.of(new SupplierOffersService.Tier(m("4"), m("9.00"))))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void purchaseFreightIsAllocatedAcrossPartialReceiptsAndReconciledAfterReturn() {
        opening("50");
        UUID orderId = Db.id(purchases.create(actor, UUID.randomUUID(),
                new PurchasingService.Order(
                        supplier,
                        List.of(new PurchasingService.Item(product, m("10"), m("10.00"))),
                        Db.today(), "Compra com frete", m("10.00"))), "id");
        assertThat(Db.decimal(purchases.detail(actor, orderId), "total"))
                .isEqualByComparingTo("110.00");
        UUID itemId = Db.id(((List<Map<String,Object>>) purchases.detail(actor, orderId)
                .get("items")).getFirst(), "id");
        purchases.receive(actor, UUID.randomUUID(),
                new PurchasingService.Receipt(List.of(new PurchasingService.Received(itemId, m("5"))),
                        "Primeira parte recebida"));
        var first = ((List<Map<String,Object>>) purchases.detail(actor, orderId).get("receipts")).getFirst();
        UUID firstReceiptId = Db.id(first, "id");
        assertThat(Db.decimal(first, "freightCost")).isEqualByComparingTo("5.00");
        assertThat(Db.decimal(first, "total")).isEqualByComparingTo("55.00");

        purchases.receive(actor, UUID.randomUUID(),
                new PurchasingService.Receipt(List.of(new PurchasingService.Received(itemId, m("5"))),
                        "Segunda parte recebida"));
        purchases.returnReceipt(actor, UUID.randomUUID(), orderId, firstReceiptId,
                "Devolução integral da primeira remessa");
        assertThat(balance("physical")).isEqualByComparingTo("55");

        purchases.receive(actor, UUID.randomUUID(),
                new PurchasingService.Receipt(List.of(new PurchasingService.Received(itemId, m("5"))),
                        "Reposição após devolução"));
        BigDecimal activePayables = db.jdbc.queryForObject(
                "SELECT COALESCE(sum(a.amount),0) FROM financial_account a JOIN purchase_receipt r "+
                "ON r.id=a.receipt_id WHERE r.purchase_id=? AND a.status<>'CANCELLED'",
                BigDecimal.class, orderId);
        assertThat(activePayables).isEqualByComparingTo("110.00");
    }

    @Test
    void receivedFreightUpdatesWeightedAverageAndSaleCostSnapshot() {
        opening("10");
        UUID orderId = Db.id(purchases.create(actor, UUID.randomUUID(),
                new PurchasingService.Order(supplier,
                        List.of(new PurchasingService.Item(product, m("10"), m("3.00"))),
                        Db.today(), "Compra com frete", m("10.00"))), "id");
        UUID purchaseItemId = Db.id(((List<Map<String,Object>>) purchases.detail(actor, orderId)
                .get("items")).getFirst(), "id");
        purchases.receive(actor, UUID.randomUUID(), new PurchasingService.Receipt(
                List.of(new PurchasingService.Received(purchaseItemId, m("10"))),
                "Recebimento integral"));
        assertThat(db.jdbc.queryForObject("SELECT cost FROM product WHERE id=?",
                BigDecimal.class, product)).isEqualByComparingTo("2.50");
        assertThat(db.jdbc.queryForObject(
                "SELECT landed_unit_cost FROM purchase_receipt_item ri JOIN purchase_item pi "
                        + "ON pi.id=ri.purchase_item_id WHERE pi.purchase_id=?",
                BigDecimal.class, orderId)).isEqualByComparingTo("4.000000");

        cash.open(actor, UUID.randomUUID(), new CashService.Open(m("0")));
        UUID saleId = Db.id(sales.create(actor, UUID.randomUUID(),
                order("2", List.of(payment("4.00", "PIX")), false)), "id");
        assertThat(db.jdbc.queryForObject("SELECT unit_cost FROM sale_item WHERE sale_id=?",
                BigDecimal.class, saleId)).isEqualByComparingTo("2.50");
        sales.cancel(actor, UUID.randomUUID(), saleId, "Devolução integral", false);
        assertThat(db.jdbc.queryForObject("SELECT cost FROM product WHERE id=?",
                BigDecimal.class, product)).isEqualByComparingTo("2.50");

        UUID receiptId = Db.id(((List<Map<String,Object>>) purchases.detail(actor, orderId)
                .get("receipts")).getFirst(), "id");
        purchases.returnReceipt(actor, UUID.randomUUID(), orderId, receiptId,
                "Devolução ao fornecedor");
        assertThat(db.jdbc.queryForObject("SELECT cost FROM product WHERE id=?",
                BigDecimal.class, product)).isEqualByComparingTo("2.50");
        assertThat(db.jdbc.queryForObject(
                "SELECT count(*) FROM product_cost_event WHERE product_id=?",
                Long.class, product)).isEqualTo(3L);
    }

    @Test
    void partialReceiptsCreateOnlyReceivedStockAndPayables() {
        opening("50");
        UUID id =
                Db.id(
                        purchases.create(
                                actor,
                                UUID.randomUUID(),
                                new PurchasingService.Order(
                                        supplier,
                                        List.of(
                                                new PurchasingService.Item(
                                                        product, m("20"), m("3.33"))),
                                        Db.today(),
                                        "Compra de teste")),
                        "id");
        UUID item =
                Db.id(
                        ((List<Map<String, Object>>) purchases.detail(actor, id).get("items"))
                                .getFirst(),
                        "id");
        assertThat(balance("physical")).isEqualByComparingTo("50");
        UUID key = UUID.randomUUID();
        var partial =
                new PurchasingService.Receipt(
                        List.of(new PurchasingService.Received(item, m("5"))),
                        "NF recebida parcialmente");
        purchases.receive(actor, key, id, partial);
        purchases.receive(actor, key, id, partial);
        assertThat(balance("physical")).isEqualByComparingTo("55");
        assertThat(purchases.detail(actor, id).get("status")).isEqualTo("PARTIAL");
        purchases.receive(
                actor,
                UUID.randomUUID(),
                id,
                new PurchasingService.Receipt(
                        List.of(new PurchasingService.Received(item, m("15"))), "Restante da NF"));
        assertThat(balance("physical")).isEqualByComparingTo("70");
        assertThat(purchases.detail(actor, id).get("status")).isEqualTo("RECEIVED");
        assertThat(
                        db.jdbc.queryForObject(
                                "SELECT sum(a.amount) FROM financial_account a JOIN"
                                        + " purchase_receipt r ON r.id=a.receipt_id WHERE"
                                        + " r.purchase_id=?",
                                BigDecimal.class,
                                id))
                .isEqualByComparingTo("66.60");
    }

    @Test
    void moneyOutCannotMakeDrawerNegative() {
        cash.open(actor, UUID.randomUUID(), new CashService.Open(m("5")));
        UUID id =
                Db.id(
                        finance.create(
                                actor,
                                UUID.randomUUID(),
                                new FinanceService.Account(
                                        "PAYABLE", "Despesa de teste", null, m("10"), Db.today())),
                        "id");
        assertThatThrownBy(
                        () -> finance.settle(actor, UUID.randomUUID(), id, payment("10", "CASH")))
                .isInstanceOf(ConflictException.class);
        assertThat(Db.decimal(finance.detail(actor, id), "paid")).isZero();
        assertThat(drawer()).isEqualByComparingTo("5");
    }

    @Test
    void saleOutboxIsDeduplicatedAndSmtpFailureDoesNotChangeSale() {
        opening("50");
        cash.open(actor, UUID.randomUUID(), new CashService.Open(m("0")));
        accounts.preferences(actor, new AccountsService.Preferences(true, true, true), true);
        UUID key = UUID.randomUUID();
        UUID id =
                Db.id(
                        sales.create(actor, key, order("1", List.of(payment("2", "PIX")), false)),
                        "id");
        sales.create(actor, key, order("1", List.of(payment("2", "PIX")), false));
        assertThat(
                        db.jdbc.queryForObject(
                                "SELECT count(*) FROM notification_outbox WHERE user_id=? AND"
                                        + " event_key=?",
                                Long.class,
                                actor.userId(),
                                "sale:" + id))
                .isEqualTo(1);
        var broken = org.mockito.Mockito.mock(JavaMailSender.class);
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("offline"))
                .when(broken)
                .send(
                        org.mockito.ArgumentMatchers.any(
                                org.springframework.mail.SimpleMailMessage.class));
        new OutboxWorker(db, broken, "sistema@ferragens.local").deliver();
        assertThat(sales.detail(actor, id).get("status")).isEqualTo("COMPLETED");
        assertThat(
                        db.one(
                                "SELECT status,attempts FROM notification_outbox WHERE event_key=?"
                                        + " AND user_id=?",
                                "sale:" + id,
                                actor.userId()))
                .containsEntry("status", "PENDING")
                .containsEntry("attempts", 1);
    }

    @Test
    void supplierReturnCompensatesTheReceiptAndItsPayable() {
        opening("50");
        UUID id =
                Db.id(
                        purchases.create(
                                actor,
                                UUID.randomUUID(),
                                new PurchasingService.Order(
                                        supplier,
                                        List.of(
                                                new PurchasingService.Item(
                                                        product, m("5"), m("1.00"))),
                                        Db.today(),
                                        "Compra")),
                        "id");
        UUID item =
                Db.id(
                        ((List<Map<String, Object>>) purchases.detail(actor, id).get("items"))
                                .getFirst(),
                        "id");
        purchases.receive(
                actor,
                UUID.randomUUID(),
                id,
                new PurchasingService.Receipt(
                        List.of(new PurchasingService.Received(item, m("5"))),
                        "Recebimento conferido"));
        UUID receipt =
                Db.id(
                        ((List<Map<String, Object>>) purchases.detail(actor, id).get("receipts"))
                                .getFirst(),
                        "id");
        UUID key = UUID.randomUUID();
        purchases.returnReceipt(actor, key, id, receipt, "Material devolvido ao fornecedor");
        purchases.returnReceipt(actor, key, id, receipt, "Material devolvido ao fornecedor");
        assertThat(balance("physical")).isEqualByComparingTo("50");
        assertThat(
                        db.one("SELECT status FROM financial_account WHERE receipt_id=?", receipt)
                                .get("status"))
                .isEqualTo("CANCELLED");
        assertThat(purchases.detail(actor, id).get("status")).isEqualTo("OPEN");
        assertThatThrownBy(
                        () ->
                                purchases.returnReceipt(
                                        actor, UUID.randomUUID(), id, receipt, "Outra devolução"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cashChangeUsesTenderedAmountButOnlyNetPaymentEntersDrawer() {
        opening("50");
        cash.open(actor, UUID.randomUUID(), new CashService.Open(m("10")));
        var body = order("15", List.of(payment("30", "CASH")), false);
        var given =
                new SalesService.Order(
                        body.customerId(),
                        body.items(),
                        body.discount(),
                        body.note(),
                        body.quote(),
                        body.dueDate(),
                        body.payments(),
                        body.expectedTotal(),
                        m("50"));
        var sale = sales.create(actor, UUID.randomUUID(), given);
        assertThat(sale.get("cashTendered")).isEqualTo("50.00");
        assertThat(sale.get("cashChange")).isEqualTo("20.00");
        assertThat(drawer()).isEqualByComparingTo("40");
        assertThatThrownBy(
                        () ->
                                sales.change(
                                        new SalesService.CashCalculation(
                                                List.of(payment("30", "CASH")), m("20"))))
                .isInstanceOf(BusinessRuleException.class);
    }

    private static BigDecimal m(String value) {
        return new BigDecimal(value);
    }

    private void opening(String qty) {
        stock.execute(
                actor,
                UUID.randomUUID(),
                new Command(
                        product,
                        Db.LOCATION,
                        Kind.INITIAL,
                        m(qty),
                        "Saldo de abertura",
                        null,
                        null));
    }

    private SalesService.Order order(
            String qty, List<FinanceService.Payment> payments, boolean quote) {
        return new SalesService.Order(
                customer,
                List.of(new SalesService.Item(product, m(qty))),
                m("0"),
                "Atendimento",
                quote,
                Db.today(),
                payments,
                null);
    }

    private SalesService.Checkout checkout(String qty) {
        return new SalesService.Checkout(
                List.of(new SalesService.Item(product, m(qty))),
                "PICKUP",
                null,
                "PAY_ON_PICKUP",
                "Pedido de teste",
                null);
    }

    private FinanceService.Payment payment(String amount, String method) {
        return new FinanceService.Payment(m(amount), method, "Conferido no teste");
    }

    private BigDecimal balance(String column) {
        return db.jdbc.queryForObject(
                "SELECT " + column + " FROM inventory_balance WHERE location_id=? AND product_id=?",
                BigDecimal.class,
                Db.LOCATION,
                product);
    }

    private BigDecimal drawer() {
        return new BigDecimal(
                ((Map<?, ?>) cash.overview(actor).get("current")).get("balance").toString());
    }

    private String productCode() {
        return Db.text(db.one("SELECT code FROM product WHERE id=?", product), "code");
    }
}
