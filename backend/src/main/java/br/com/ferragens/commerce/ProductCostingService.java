package br.com.ferragens.commerce;

import br.com.ferragens.shared.ConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Custeio móvel por média ponderada. Cada alteração fica vinculada à movimentação
 * física correspondente, permitindo auditoria e compensação de devoluções.
 */
@Service
@Transactional
public class ProductCostingService {
    private static final int COST_SCALE = 2;
    private final JdbcTemplate jdbc;

    public ProductCostingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BigDecimal currentCost(UUID productId) {
        return jdbc.queryForObject("SELECT cost FROM product WHERE id=? FOR UPDATE",
                BigDecimal.class, productId);
    }

    /** Registra uma entrada valorizada (compra recebida ou devolução de venda). */
    public BigDecimal add(UUID productId, UUID movementId, UUID sourceId, String sourceType,
                          BigDecimal quantity, BigDecimal unitCost) {
        BigDecimal qty = quantity;
        BigDecimal costIn = unitCost;
        BigDecimal stockAfter = stock(productId);
        BigDecimal stockBefore = stockAfter.subtract(qty);
        if (stockBefore.signum() < 0)
            throw new ConflictException("Saldo físico inconsistente ao valorizar a entrada.");
        BigDecimal before = currentCost(productId);
        BigDecimal after = stockBefore.signum() == 0
                ? costIn
                : stockBefore.multiply(before).add(qty.multiply(costIn))
                    .divide(stockAfter, COST_SCALE + 6, RoundingMode.HALF_UP);
        after = after.setScale(COST_SCALE, RoundingMode.HALF_UP);
        save(productId, movementId, sourceId, sourceType, qty, costIn,
                stockBefore, stockAfter, before, after);
        jdbc.update("UPDATE product SET cost=?,updated_at=now() WHERE id=?", after, productId);
        return after;
    }

    /** Retira do valor do estoque a parcela recebida originalmente, sem reescrever vendas antigas. */
    public BigDecimal remove(UUID productId, UUID movementId, UUID sourceId, String sourceType,
                             BigDecimal quantity, BigDecimal unitCost) {
        BigDecimal qty = quantity;
        BigDecimal costOut = unitCost;
        BigDecimal stockAfter = stock(productId);
        BigDecimal stockBefore = stockAfter.add(qty);
        BigDecimal before = currentCost(productId);
        BigDecimal remainingValue = stockBefore.multiply(before).subtract(qty.multiply(costOut));
        if (remainingValue.signum() < 0)
            throw new ConflictException("A devolução excede o valor contábil disponível. "
                    + "Faça a conferência do estoque e do custo antes de devolver este recebimento.");
        BigDecimal after = stockAfter.signum() == 0
                ? before
                : remainingValue.divide(stockAfter, COST_SCALE + 6, RoundingMode.HALF_UP)
                    .setScale(COST_SCALE, RoundingMode.HALF_UP);
        save(productId, movementId, sourceId, sourceType, qty, costOut,
                stockBefore, stockAfter, before, after);
        jdbc.update("UPDATE product SET cost=?,updated_at=now() WHERE id=?", after, productId);
        return after;
    }

    private BigDecimal stock(UUID productId) {
        return jdbc.queryForObject(
                "SELECT physical FROM inventory_balance WHERE location_id=? AND product_id=? FOR UPDATE",
                BigDecimal.class, Db.LOCATION, productId);
    }

    private void save(UUID productId, UUID movementId, UUID sourceId, String sourceType,
                      BigDecimal quantity, BigDecimal unitCost, BigDecimal stockBefore,
                      BigDecimal stockAfter, BigDecimal costBefore, BigDecimal costAfter) {
        jdbc.update("""
                INSERT INTO product_cost_event
                    (id,product_id,movement_id,source_id,source_type,quantity,unit_cost,
                     stock_before,stock_after,cost_before,cost_after)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), productId, movementId, sourceId, sourceType,
                quantity, unitCost, stockBefore, stockAfter, costBefore, costAfter);
    }
}
