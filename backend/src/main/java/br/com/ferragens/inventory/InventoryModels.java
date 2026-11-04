package br.com.ferragens.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InventoryModels {
    private InventoryModels() { }

    public enum Kind { INITIAL, ENTRY, EXIT, LOSS, COUNT, MINIMUM, REVERSAL }

    public record Location(UUID id, String name) { }

    public record Command(
            @NotNull UUID productId, @NotNull UUID locationId, @NotNull Kind kind,
            @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal quantity,
            @NotBlank @Size(min = 3, max = 500) String reason,
            Long expectedRevision, UUID reversesMovementId) { }

    public record Balance(UUID productId, UUID locationId, String code, String name, String unit,
                          int quantityScale, String quantityStep, String physical, String reserved,
                          String unavailable, String available, String minimumQuantity, long revision,
                          String status, boolean active) { }

    public record Movement(UUID id, UUID productId, UUID locationId, String code, String productName,
                           String unit, Kind kind, String quantity, String delta, String balanceBefore,
                           String balanceAfter, String minimumBefore, String minimumAfter, String reason,
                           String actorName, Instant occurredAt, UUID reversesMovementId, boolean reversed) { }

    public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> Page<T> of(List<T> rows, int page, int size, long total) {
            return new Page<>(rows, page, size, total, (int) Math.ceil((double) total / size));
        }
    }
}
