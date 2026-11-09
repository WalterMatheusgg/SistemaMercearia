package br.com.ferragens.product;

import br.com.ferragens.auth.CurrentActorService.Actor;
import br.com.ferragens.commerce.Db;
import br.com.ferragens.shared.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

/** Pesquisa somente leitura. Identificar um item NÃO movimenta estoque nem efetiva venda. */
@Service
@Transactional(readOnly = true)
public class BarcodeCatalogService {
    private final JdbcTemplate jdbc;

    public BarcodeCatalogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Item(UUID productId, String barcode, String name, String unit, String price,
                       String physical, String available, String minimumQuantity,
                       int quantityScale, String quantityStep, long revision, boolean active) {}

    private static final String SELECT = """
        SELECT p.id, p.barcode, p.name, p.price, p.active, u.code AS unit,
               u.quantity_scale, u.quantity_step,
               coalesce(b.physical, 0) AS physical,
               coalesce(b.physical - b.reserved - b.unavailable, 0) AS available,
               coalesce(b.minimum_quantity, 0) AS minimum_quantity,
               coalesce(b.revision, 0) AS revision
          FROM inventory_location l
          JOIN product p ON true
          JOIN unit u ON u.code = p.base_unit_code
          LEFT JOIN inventory_balance b ON b.location_id = l.id AND b.product_id = p.id
         WHERE l.id = ? AND l.store_id = ? AND l.active AND p.active
        """;

    public Item byBarcode(Actor actor, UUID locationId, String rawCode) {
        String code = rawCode == null ? "" : rawCode.strip().toUpperCase(Locale.ROOT);
        if (!code.matches("LF[0-9]{10}")) {
            throw new NotFoundException("Código de barras não encontrado. Confira a etiqueta.");
        }
        return query(actor, locationId, " AND p.barcode = ?", code);
    }

    public Item byProduct(Actor actor, UUID locationId, UUID productId) {
        if (productId == null) throw new NotFoundException("Produto não encontrado.");
        return query(actor, locationId, " AND p.id = ?", productId);
    }

    private Item query(Actor actor, UUID locationId, String predicate, Object value) {
        UUID location = locationId == null ? Db.LOCATION : locationId;
        return jdbc.query(SELECT + predicate + " LIMIT 1", (rs, row) -> new Item(
                rs.getObject("id", UUID.class), rs.getString("barcode"), rs.getString("name"),
                rs.getString("unit"), decimal(rs.getBigDecimal("price")),
                decimal(rs.getBigDecimal("physical")), decimal(rs.getBigDecimal("available")),
                decimal(rs.getBigDecimal("minimum_quantity")), rs.getInt("quantity_scale"),
                decimal(rs.getBigDecimal("quantity_step")), rs.getLong("revision"),
                rs.getBoolean("active")), location, actor.storeId(), value)
            .stream().findFirst()
            .orElseThrow(() -> new NotFoundException("Material não encontrado neste local de estoque."));
    }

    private static String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
