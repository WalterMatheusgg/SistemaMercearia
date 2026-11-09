package br.com.ferragens.inventory;

import static br.com.ferragens.inventory.InventoryModels.*;

import br.com.ferragens.auth.CurrentActorService.Actor;
import br.com.ferragens.shared.BusinessRuleException;
import br.com.ferragens.shared.ConflictException;
import br.com.ferragens.shared.NotFoundException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class InventoryService {
    private final JdbcTemplate jdbc;
    private final br.com.ferragens.notification.Notifications notices;

    public InventoryService(
            JdbcTemplate jdbc, br.com.ferragens.notification.Notifications notices) {
        this.jdbc = jdbc;
        this.notices = notices;
    }

    @Transactional(readOnly = true)
    public List<Location> locations(Actor actor) {
        return jdbc.query(
                "SELECT id, name FROM inventory_location WHERE store_id = ? AND active ORDER BY"
                        + " name, id",
                (rs, row) -> new Location(rs.getObject("id", UUID.class), rs.getString("name")),
                actor.storeId());
    }

    /** Compatibilidade para consultas existentes; filtro de categoria é opcional. */
    @Transactional(readOnly = true)
    public Page<Balance> balances(
            Actor actor, UUID locationId, String query, boolean onlyLow,
            boolean includeInactive, int page, int size) {
        return balances(actor, locationId, query, onlyLow, includeInactive, page, size, null);
    }

    @Transactional(readOnly = true)
    public Page<Balance> balances(
            Actor actor,
            UUID locationId,
            String query,
            boolean onlyLow,
            boolean includeInactive,
            int page,
            int size,
            UUID categoryId) {
        requireLocation(actor, locationId);
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.clamp(size, 1, 100);
        String from =
                " FROM product p JOIN unit u ON u.code = p.base_unit_code LEFT JOIN"
                    + " inventory_balance b ON b.product_id = p.id AND b.location_id = ? WHERE 1=1"
                    + " ";
        var args = new ArrayList<Object>();
        args.add(locationId);
        if (!includeInactive) from += " AND p.active = TRUE ";
        if (query != null && !query.isBlank()) {
            from += " AND (lower(p.name) LIKE ? ESCAPE '\\' OR lower(p.code) LIKE ? ESCAPE '\\'"
                    + " OR lower(p.barcode) LIKE ? ESCAPE '\\') ";
            String pattern =
                    "%"
                            + query.trim()
                                    .toLowerCase(Locale.ROOT)
                                    .replace("\\", "\\\\")
                                    .replace("%", "\\%")
                                    .replace("_", "\\_")
                            + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        // Filtrar antes da paginação garante contagem e páginas corretas por categoria.
        if (categoryId != null) {
            from += " AND p.category_id = ? ";
            args.add(categoryId);
        }
        if (onlyLow)
            from +=
                    " AND coalesce(b.physical-b.reserved-b.unavailable,0) <="
                            + " coalesce(b.minimum_quantity,0) ";
        long total = jdbc.queryForObject("SELECT count(*)" + from, Long.class, args.toArray());
        var selectArgs = new ArrayList<>(args);
        selectArgs.add(boundedSize);
        selectArgs.add((long) boundedPage * boundedSize);
        var rows =
                jdbc.query(
                        "SELECT p.id, p.code, p.name, p.active, u.code AS unit, u.quantity_scale,"
                            + " u.quantity_step, coalesce(b.physical,0) AS physical,"
                            + " coalesce(b.reserved,0) AS reserved, coalesce(b.unavailable,0) AS"
                            + " unavailable, coalesce(b.minimum_quantity,0) AS minimum_quantity,"
                            + " coalesce(b.revision,0) AS revision"
                                + from
                                + " ORDER BY p.name, p.id LIMIT ? OFFSET ?",
                        (rs, row) -> balance(rs, locationId),
                        selectArgs.toArray());
        return Page.of(rows, boundedPage, boundedSize, total);
    }

    @Transactional(readOnly = true)
    public Page<Movement> history(
            Actor actor, UUID locationId, UUID productId, Kind kind, int page, int size) {
        requireLocation(actor, locationId);
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.clamp(size, 1, 100);
        String where = " WHERE m.store_id = ? AND m.location_id = ? ";
        var args = new ArrayList<Object>();
        args.add(actor.storeId());
        args.add(locationId);
        if (productId != null) {
            where += " AND m.product_id = ? ";
            args.add(productId);
        }
        if (kind != null) {
            where += " AND m.kind = ? ";
            args.add(kind.name());
        }
        long total =
                jdbc.queryForObject(
                        "SELECT count(*) FROM inventory_movement m" + where,
                        Long.class,
                        args.toArray());
        args.add(boundedSize);
        args.add((long) boundedPage * boundedSize);
        return Page.of(
                jdbc.query(
                        movementSelect()
                                + where
                                + " ORDER BY m.occurred_at DESC, m.id DESC LIMIT ? OFFSET ?",
                        (rs, row) -> movement(rs),
                        args.toArray()),
                boundedPage,
                boundedSize,
                total);
    }

    public Movement execute(Actor actor, UUID idempotencyKey, Command command) {
        validateCommand(command);
        if (idempotencyKey == null) throw new BusinessRuleException("Informe a chave da operação.");
        requireLocation(actor, command.locationId());
        String fingerprint = fingerprint(actor, command);
        // A serialização da chave também cobre o mesmo comando enviado para itens diferentes.
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                (RowCallbackHandler) rs -> {},
                actor.storeId() + ":" + idempotencyKey);
        var existing =
                jdbc.query(
                        "SELECT id, request_fingerprint FROM inventory_movement WHERE store_id = ?"
                                + " AND idempotency_key = ?",
                        (rs, row) ->
                                new SavedCommand(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("request_fingerprint")),
                        actor.storeId(),
                        idempotencyKey);
        if (!existing.isEmpty()) {
            if (!existing.getFirst().fingerprint().equals(fingerprint)) {
                throw new ConflictException(
                        "Esta chave já foi usada com outro conteúdo. Confira o histórico antes de"
                                + " registrar outra operação.");
            }
            return findMovement(actor, existing.getFirst().id());
        }

        // A mesma ordem de bloqueios é usada para proteger a unidade do produto e o saldo.
        ProductData product =
                jdbc
                        .query(
                                "SELECT p.id, p.code, p.name, p.active, u.code AS unit,"
                                    + " u.quantity_scale, u.quantity_step FROM product p JOIN unit"
                                    + " u ON u.code = p.base_unit_code WHERE p.id = ? FOR UPDATE OF"
                                    + " p",
                                (rs, row) ->
                                        new ProductData(
                                                rs.getString("code"),
                                                rs.getString("name"),
                                                rs.getString("unit"),
                                                rs.getInt("quantity_scale"),
                                                rs.getBigDecimal("quantity_step"),
                                                rs.getBoolean("active")),
                                command.productId())
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new NotFoundException("Produto não encontrado."));
        validateQuantity(command.quantity(), product);
        if (!product.active() && (command.kind() == Kind.INITIAL || command.kind() == Kind.ENTRY)) {
            throw new BusinessRuleException("Reative o produto antes de registrar uma entrada.");
        }
        jdbc.update(
                "INSERT INTO inventory_balance (location_id, product_id) VALUES (?,?) ON CONFLICT"
                        + " DO NOTHING",
                command.locationId(),
                command.productId());
        BalanceData before =
                jdbc.queryForObject(
                        "SELECT physical, reserved, unavailable, minimum_quantity, revision FROM"
                                + " inventory_balance WHERE location_id = ? AND product_id = ? FOR"
                                + " UPDATE",
                        (rs, row) ->
                                new BalanceData(
                                        rs.getBigDecimal("physical"),
                                        rs.getBigDecimal("reserved"),
                                        rs.getBigDecimal("unavailable"),
                                        rs.getBigDecimal("minimum_quantity"),
                                        rs.getLong("revision")),
                        command.locationId(),
                        command.productId());

        BigDecimal delta;
        BigDecimal minimum = before.minimum();
        switch (command.kind()) {
            case INITIAL -> {
                boolean used =
                        Boolean.TRUE.equals(
                                jdbc.queryForObject(
                                        "SELECT EXISTS (SELECT 1 FROM inventory_movement WHERE"
                                                + " location_id = ? AND product_id = ? AND kind <>"
                                                + " 'MINIMUM')",
                                        Boolean.class,
                                        command.locationId(),
                                        command.productId()));
                if (used)
                    throw new ConflictException(
                            "O saldo inicial já foi encerrado para este item. Registre uma entrada"
                                    + " normal.");
                delta = command.quantity();
            }
            case ENTRY -> delta = command.quantity();
            case EXIT, LOSS -> delta = command.quantity().negate();
            case COUNT -> {
                if (command.expectedRevision() == null
                        || command.expectedRevision() != before.revision()) {
                    throw new ConflictException(
                            "O saldo mudou desde a consulta. Atualize e confira a contagem"
                                    + " novamente.");
                }
                delta = command.quantity().subtract(before.physical());
            }
            case MINIMUM -> {
                delta = BigDecimal.ZERO;
                minimum = command.quantity();
            }
            case REVERSAL -> {
                Movement original = findMovement(actor, command.reversesMovementId());
                if (!original.locationId().equals(command.locationId())
                        || !original.productId().equals(command.productId())) {
                    throw new BusinessRuleException(
                            "A correção deve usar o produto e o local originais.");
                }
                if (original.reversed()
                        || original.kind() == Kind.REVERSAL
                        || original.kind() == Kind.MINIMUM
                        || new BigDecimal(original.delta()).signum() == 0) {
                    throw new ConflictException(
                            "Esta operação não permite outro estorno. Registre o ajuste"
                                    + " apropriado.");
                }
                delta = new BigDecimal(original.delta()).negate();
                if (delta.abs().compareTo(command.quantity()) != 0)
                    throw new BusinessRuleException("O estorno deve ser integral.");
            }
            default -> throw new BusinessRuleException("Tipo de movimentação inválido.");
        }
        BigDecimal physical = before.physical().add(delta);
        if (physical.subtract(before.reserved()).subtract(before.unavailable()).signum() < 0) {
            throw new ConflictException(
                    "Quantidade insuficiente no estoque disponível. Nenhuma saída foi registrada.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update(
                "UPDATE inventory_balance SET physical = ?, minimum_quantity = ?, revision ="
                    + " revision+1, updated_at = now() WHERE location_id = ? AND product_id = ?",
                physical,
                minimum,
                command.locationId(),
                command.productId());
        jdbc.update(
                "INSERT INTO inventory_movement (id, store_id, location_id, product_id, kind,"
                    + " product_code, product_name, unit_code, quantity, delta, balance_before,"
                    + " balance_after, minimum_before, minimum_after, reason, actor_id, actor_name,"
                    + " idempotency_key, request_fingerprint, reverses_movement_id) VALUES"
                    + " (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id,
                actor.storeId(),
                command.locationId(),
                command.productId(),
                command.kind().name(),
                product.code(),
                product.name(),
                product.unit(),
                command.quantity(),
                delta,
                before.physical(),
                physical,
                before.minimum(),
                minimum,
                command.reason().trim(),
                actor.userId(),
                actor.username(),
                idempotencyKey,
                fingerprint,
                command.reversesMovementId());
        notices.stock(
                actor.storeId(),
                command.productId(),
                product.name(),
                product.unit(),
                before.physical().subtract(before.reserved()).subtract(before.unavailable()),
                physical.subtract(before.reserved()).subtract(before.unavailable()),
                before.minimum(),
                minimum,
                command.kind().name(),
                id);
        return findMovement(actor, id);
    }

    private void requireLocation(Actor actor, UUID locationId) {
        boolean exists =
                Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "SELECT EXISTS (SELECT 1 FROM inventory_location "
                                        + "WHERE id = ? AND store_id = ? AND active)",
                                Boolean.class,
                                locationId,
                                actor.storeId()));
        if (!exists) throw new NotFoundException("Local de estoque não encontrado para esta loja.");
    }

    private void validateCommand(Command command) {
        if (command == null
                || command.kind() == null
                || command.productId() == null
                || command.locationId() == null
                || command.quantity() == null
                || command.quantity().signum() < 0
                || command.reason() == null
                || command.reason().trim().length() < 3
                || command.reason().trim().length() > 500) {
            throw new BusinessRuleException(
                    "Informe produto, local, quantidade válida e motivo com pelo menos 3"
                            + " caracteres.");
        }
        if (command.quantity().signum() == 0
                && command.kind() != Kind.COUNT
                && command.kind() != Kind.MINIMUM) {
            throw new BusinessRuleException("A quantidade deve ser maior que zero.");
        }
        if ((command.kind() == Kind.REVERSAL) != (command.reversesMovementId() != null)) {
            throw new BusinessRuleException("Informe a operação original somente para estornos.");
        }
    }

    private void validateQuantity(BigDecimal value, ProductData product) {
        if (value.stripTrailingZeros().scale() > product.scale()
                || value.precision() - value.scale() > 15
                || value.remainder(product.step()).signum() != 0) {
            throw new BusinessRuleException(
                    "A unidade "
                            + product.unit()
                            + " aceita incrementos de "
                            + quantity(product.step())
                            + ".");
        }
    }

    private Movement findMovement(Actor actor, UUID id) {
        return jdbc
                .query(
                        movementSelect() + " WHERE m.store_id = ? AND m.id = ?",
                        (rs, row) -> movement(rs),
                        actor.storeId(),
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Movimentação não encontrada."));
    }

    private static String movementSelect() {
        return "SELECT m.*, EXISTS (SELECT 1 FROM inventory_movement r WHERE r.reverses_movement_id"
                + " = m.id) AS reversed FROM inventory_movement m";
    }

    private static Movement movement(ResultSet rs) throws SQLException {
        return new Movement(
                rs.getObject("id", UUID.class),
                rs.getObject("product_id", UUID.class),
                rs.getObject("location_id", UUID.class),
                rs.getString("product_code"),
                rs.getString("product_name"),
                rs.getString("unit_code"),
                Kind.valueOf(rs.getString("kind")),
                quantity(rs.getBigDecimal("quantity")),
                quantity(rs.getBigDecimal("delta")),
                quantity(rs.getBigDecimal("balance_before")),
                quantity(rs.getBigDecimal("balance_after")),
                quantity(rs.getBigDecimal("minimum_before")),
                quantity(rs.getBigDecimal("minimum_after")),
                rs.getString("reason"),
                rs.getString("actor_name"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getObject("reverses_movement_id", UUID.class),
                rs.getBoolean("reversed"));
    }

    private static Balance balance(ResultSet rs, UUID locationId) throws SQLException {
        BigDecimal physical = rs.getBigDecimal("physical");
        BigDecimal reserved = rs.getBigDecimal("reserved");
        BigDecimal unavailable = rs.getBigDecimal("unavailable");
        BigDecimal available = physical.subtract(reserved).subtract(unavailable);
        BigDecimal minimum = rs.getBigDecimal("minimum_quantity");
        String status =
                available.signum() == 0
                        ? "EMPTY"
                        : available.compareTo(minimum) <= 0 ? "LOW" : "NORMAL";
        return new Balance(
                rs.getObject("id", UUID.class),
                locationId,
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("unit"),
                rs.getInt("quantity_scale"),
                quantity(rs.getBigDecimal("quantity_step")),
                quantity(physical),
                quantity(reserved),
                quantity(unavailable),
                quantity(available),
                quantity(minimum),
                rs.getLong("revision"),
                status,
                rs.getBoolean("active"));
    }

    private static String quantity(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String fingerprint(Actor actor, Command command) {
        String content =
                actor.userId()
                        + "|"
                        + command.productId()
                        + "|"
                        + command.locationId()
                        + "|"
                        + command.kind()
                        + "|"
                        + quantity(command.quantity())
                        + "|"
                        + command.reason().trim()
                        + "|"
                        + command.expectedRevision()
                        + "|"
                        + command.reversesMovementId();
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record SavedCommand(UUID id, String fingerprint) {}

    private record ProductData(
            String code, String name, String unit, int scale, BigDecimal step, boolean active) {}

    private record BalanceData(
            BigDecimal physical,
            BigDecimal reserved,
            BigDecimal unavailable,
            BigDecimal minimum,
            long revision) {}
}
