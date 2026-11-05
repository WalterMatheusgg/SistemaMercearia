package br.com.ferragens.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "base_unit_code", nullable = false)
    private Unit baseUnit;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal cost;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    public Product(String code, String name, String description, Category category, Unit baseUnit,
                   BigDecimal cost, BigDecimal price) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.category = category;
        this.baseUnit = baseUnit;
        this.cost = cost;
        this.price = price;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void update(String code, String name, String description, Category category, Unit baseUnit,
                       BigDecimal cost, BigDecimal price) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.category = category;
        this.baseUnit = baseUnit;
        this.cost = cost;
        this.price = price;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public Unit getBaseUnit() { return baseUnit; }
    public BigDecimal getCost() { return cost; }
    public BigDecimal getPrice() { return price; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}