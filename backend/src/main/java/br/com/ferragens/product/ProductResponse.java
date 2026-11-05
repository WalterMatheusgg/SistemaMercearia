package br.com.ferragens.product;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(UUID id, String code, String name, String description,
                              UUID categoryId, String categoryName, String baseUnitCode,
                              String baseUnitName, String cost, String price,
                              boolean active, Instant createdAt, Instant updatedAt, String brand) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getCode(), product.getName(),
                product.getDescription(), product.getCategory().getId(), product.getCategory().getName(),
                product.getBaseUnit().getCode(), product.getBaseUnit().getName(),
                product.getCost().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                product.getPrice().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                product.isActive(), product.getCreatedAt(), product.getUpdatedAt(), product.getBrand());
    }
}