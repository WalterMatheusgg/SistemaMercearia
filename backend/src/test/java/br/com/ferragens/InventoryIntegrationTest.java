package br.com.ferragens;

import br.com.ferragens.auth.CurrentActorService.Actor;
import br.com.ferragens.inventory.InventoryModels.*;
import br.com.ferragens.inventory.InventoryService;
import br.com.ferragens.shared.BusinessRuleException;
import br.com.ferragens.shared.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InventoryIntegrationTest {
    private static final UUID STORE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID LOCATION = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final String SCHEMA = "inventory_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry properties) { IntegrationServices.configure(properties, SCHEMA); }

    @Autowired InventoryService inventory;
    @Autowired JdbcTemplate jdbc;
    private UUID product;
    private Actor actor;
    private String code;

    @BeforeEach
    void prepareItem() {
        UUID user = UUID.randomUUID();
        String username = "stock-" + user.toString().substring(0, 12);
        jdbc.update("INSERT INTO app_user (id, store_id, username, password_hash) VALUES (?,?,?,?)", user, STORE, username, "not-used-for-service-tests");
        actor = new Actor(user, STORE, username);
        product = UUID.randomUUID(); code = "IT-" + product.toString().substring(0, 18);
        jdbc.update("INSERT INTO product (id, code, name, category_id, base_unit_code, cost, price) VALUES (?,?,?,?,'UN',1,2)",
                product, code, "Produto " + code, UUID.fromString("00000000-0000-0000-0000-000000000104"));
    }

    @Test
    void calculatesEntriesExitsAndKeepsHistory() {
        move(Kind.INITIAL, "50"); move(Kind.EXIT, "15");
        assertThat(balance().physical()).isEqualTo("35");
        move(Kind.ENTRY, "20");
        assertThat(balance().available()).isEqualTo("55");
        assertThat(history().totalElements()).isEqualTo(3);
        assertThat(history().content()).allMatch(m -> m.actorName().equals(actor.username()));
    }

    @Test
    void retriesTheSameCommandWithoutAnotherMovement() {
        UUID key = UUID.randomUUID(); Command command = command(Kind.INITIAL, "50");
        Movement first = inventory.execute(actor, key, command);
        Movement repeated = inventory.execute(actor, key, command);
        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(balance().physical()).isEqualTo("50");
        assertThat(history().totalElements()).isEqualTo(1);
        assertThatThrownBy(() -> inventory.execute(actor, key, command(Kind.INITIAL, "51"))).isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsInsufficientStockWithoutPartialWrites() {
        move(Kind.INITIAL, "10");
        assertThatThrownBy(() -> move(Kind.EXIT, "11")).isInstanceOf(ConflictException.class);
        assertThat(balance().physical()).isEqualTo("10");
        assertThat(history().totalElements()).isEqualTo(1);
    }

    @Test
    void rollsBackBalanceWhenTheHistoryInsertFails() {
        move(Kind.INITIAL, "50");
        Actor invalidActor = new Actor(UUID.randomUUID(), STORE, "usuário inexistente");
        assertThatThrownBy(() -> inventory.execute(invalidActor, UUID.randomUUID(), command(Kind.EXIT, "15")))
                .isInstanceOf(DataAccessException.class);
        assertThat(balance().physical()).isEqualTo("50");
        assertThat(history().totalElements()).isEqualTo(1);
    }

    @Test
    void concurrentExitsCannotConsumeMoreThanAvailable() throws Exception {
        move(Kind.INITIAL, "10");
        assertThat(concurrently(() -> move(Kind.EXIT, "7"))).isEqualTo(1);
        assertThat(balance().physical()).isEqualTo("3");
        assertThat(history().totalElements()).isEqualTo(2);
    }

    @Test
    void concurrentOpeningsCreateOnlyOneBalanceAndOpening() throws Exception {
        assertThat(concurrently(() -> move(Kind.INITIAL, "10"))).isEqualTo(1);
        assertThat(balance().physical()).isEqualTo("10");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_balance WHERE product_id=?", Long.class, product)).isEqualTo(1);
        assertThat(history().totalElements()).isEqualTo(1);
    }

    @Test
    void staleCountDoesNotOverwriteANewerEntry() {
        move(Kind.INITIAL, "50"); long revision = balance().revision(); move(Kind.ENTRY, "20");
        var count = new Command(product, LOCATION, Kind.COUNT, new BigDecimal("45"), "Conferência física", revision, null);
        assertThatThrownBy(() -> inventory.execute(actor, UUID.randomUUID(), count)).isInstanceOf(ConflictException.class);
        assertThat(balance().physical()).isEqualTo("70");
        assertThat(history().totalElements()).isEqualTo(2);
    }

    @Test
    void validatesUnitIncrementsAndAllowsAZeroCount() {
        assertThatThrownBy(() -> move(Kind.INITIAL, "1.5")).isInstanceOf(BusinessRuleException.class);
        jdbc.update("UPDATE product SET base_unit_code='M' WHERE id=?", product);
        assertThatThrownBy(() -> move(Kind.INITIAL, "1.001")).isInstanceOf(BusinessRuleException.class);
        move(Kind.INITIAL, "1.25");
        inventory.execute(actor, UUID.randomUUID(), new Command(product, LOCATION, Kind.COUNT, BigDecimal.ZERO,
                "Contagem conferida", balance().revision(), null));
        assertThat(balance().physical()).isEqualTo("0");
    }

    @Test
    void reversalPreservesTheOriginalAndCannotBeRepeated() {
        move(Kind.INITIAL, "50"); Movement exit = move(Kind.EXIT, "15");
        var command = new Command(product, LOCATION, Kind.REVERSAL, new BigDecimal("15"), "Saída registrada por engano", null, exit.id());
        Movement reversal = inventory.execute(actor, UUID.randomUUID(), command);
        assertThat(reversal.reversesMovementId()).isEqualTo(exit.id());
        assertThat(balance().physical()).isEqualTo("50");
        assertThatThrownBy(() -> inventory.execute(actor, UUID.randomUUID(), command)).isInstanceOf(ConflictException.class);
        assertThat(history().content()).anyMatch(m -> m.id().equals(exit.id()) && m.reversed());
    }

    @Test
    void preventsEditingOrDeletingConfirmedHistory() {
        Movement opening = move(Kind.INITIAL, "50");
        assertThatThrownBy(() -> jdbc.update("UPDATE inventory_movement SET reason='Alterado' WHERE id=?", opening.id())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM inventory_movement WHERE id=?", opening.id())).isInstanceOf(DataAccessException.class);
        assertThat(history().totalElements()).isEqualTo(1);
    }

    @Test
    void configurationCanPrecedeOpeningAndHistoryKeepsProductSnapshot() {
        move(Kind.MINIMUM, "10"); move(Kind.INITIAL, "5");
        assertThat(balance().status()).isEqualTo("LOW");
        assertThat(inventory.balances(actor, LOCATION, code, true, false, 0, 20).totalElements()).isEqualTo(1);
        jdbc.update("UPDATE product SET name='Nome novo' WHERE id=?", product);
        assertThat(history().content()).allMatch(m -> m.productName().equals("Produto " + code));
    }

    @Test
    void anotherStoreCannotUseThisLocation() {
        Actor otherStore = new Actor(actor.userId(), UUID.randomUUID(), actor.username());
        assertThatThrownBy(() -> inventory.execute(otherStore, UUID.randomUUID(), command(Kind.ENTRY, "1")))
                .isInstanceOf(br.com.ferragens.shared.NotFoundException.class);
    }

    private int concurrently(Callable<Movement> command) throws Exception {
        CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> task = () -> { ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout");
            try { command.call(); return true; } catch (ConflictException expected) { return false; } };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(task); var second = pool.submit(task);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return (first.get(20, TimeUnit.SECONDS) ? 1 : 0) + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
        }
    }

    private Movement move(Kind kind, String quantity) { return inventory.execute(actor, UUID.randomUUID(), command(kind, quantity)); }
    private Command command(Kind kind, String quantity) { return new Command(product, LOCATION, kind, new BigDecimal(quantity), "Movimentação de teste", null, null); }
    private Balance balance() { return inventory.balances(actor, LOCATION, code, false, true, 0, 20).content().getFirst(); }
    private Page<Movement> history() { return inventory.history(actor, LOCATION, product, null, 0, 20); }
}
