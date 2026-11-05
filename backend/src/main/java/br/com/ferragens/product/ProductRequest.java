package br.com.ferragens.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(
        @Size(max = 40) String code,
        @Size(max = 160) String name,
        @Size(max = 2000) String description,
        UUID categoryId,
        String baseUnitCode,
        @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal cost,
        @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal price,
        @Size(max = 80) String brand) {
    /** Mantém compatibilidade com chamadas anteriores e importação em lote. */
    public ProductRequest(String code, String name, String description, UUID categoryId,
                          String baseUnitCode, BigDecimal cost, BigDecimal price) {
        this(code, name, description, categoryId, baseUnitCode, cost, price, null);
    }
}