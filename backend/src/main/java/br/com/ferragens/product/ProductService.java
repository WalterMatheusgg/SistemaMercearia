package br.com.ferragens.product;

import br.com.ferragens.shared.ConflictException;
import br.com.ferragens.shared.NotFoundException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final UnitRepository units;
    private final JdbcTemplate jdbc;

    public ProductService(
            ProductRepository products,
            CategoryRepository categories,
            UnitRepository units,
            JdbcTemplate jdbc) {
        this.products = products;
        this.categories = categories;
        this.units = units;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(String query, Boolean active, Pageable pageable) {
        String normalizedQuery = StringUtils.hasText(query) ? query.trim() : null;
        Specification<Product> specification =
                (root, criteriaQuery, builder) -> {
                    var predicates =
                            new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                    if (normalizedQuery != null) {
                        String pattern = "%" + normalizedQuery.toLowerCase(Locale.ROOT) + "%";
                        predicates.add(
                                builder.or(
                                        builder.like(builder.lower(root.get("name")), pattern),
                                        builder.like(builder.lower(root.get("code")), pattern)));
                    }
                    if (active != null) {
                        predicates.add(builder.equal(root.get("active"), active));
                    }
                    return builder.and(
                            predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
                };
        return products.findAll(specification, pageable).map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return ProductResponse.from(find(id));
    }

    public ProductResponse create(ProductRequest request) {
        String code = normalizeCode(request.code());
        if (products.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Já existe um produto com este código.");
        }
        return ProductResponse.from(
                products.save(
                        new Product(
                                code,
                                request.name().trim(),
                                request.description(),
                                findCategory(request.categoryId()),
                                findUnit(request.baseUnitCode()),
                                request.cost(),
                                request.price())));
    }

    public ProductResponse update(UUID id, ProductRequest request) {
        var locked =
                jdbc.query(
                        "SELECT id FROM product WHERE id = ? FOR UPDATE",
                        (rs, row) -> rs.getObject(1, UUID.class),
                        id);
        if (locked.isEmpty()) throw new NotFoundException("Produto não encontrado.");
        Product product = find(id);
        Unit unit = findUnit(request.baseUnitCode());
        if (!product.getBaseUnit().getCode().equals(unit.getCode())
                && Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "SELECT EXISTS (SELECT 1 FROM inventory_movement WHERE product_id ="
                                    + " ?) OR EXISTS(SELECT 1 FROM sale_item WHERE product_id=?) OR"
                                    + " EXISTS(SELECT 1 FROM purchase_item WHERE product_id=?)",
                                Boolean.class,
                                id,
                                id,
                                id))) {
            throw new ConflictException(
                    "A unidade não pode mudar após movimentações. Cadastre outro item para a nova"
                            + " medida.");
        }
        String code = normalizeCode(request.code());
        if (products.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new ConflictException("Já existe um produto com este código.");
        }
        product.update(
                code,
                request.name().trim(),
                request.description(),
                findCategory(request.categoryId()),
                unit,
                request.cost(),
                request.price());
        return ProductResponse.from(product);
    }

    public void deactivate(UUID id) {
        find(id).deactivate();
    }

    private Product find(UUID id) {
        return products.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("Produto não encontrado."));
    }

    private Category findCategory(UUID id) {
        return categories
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Categoria não encontrada."));
    }

    private Unit findUnit(String code) {
        return units.findById(code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("Unidade não encontrada."));
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
