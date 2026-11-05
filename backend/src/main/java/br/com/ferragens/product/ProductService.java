package br.com.ferragens.product;

import br.com.ferragens.auth.CurrentActorService.Actor;
import br.com.ferragens.commerce.Db;
import br.com.ferragens.shared.BusinessRuleException;
import br.com.ferragens.shared.ConflictException;
import br.com.ferragens.shared.NotFoundException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final UnitRepository units;
    private final JdbcTemplate jdbc;
    private final Db database;

    public ProductService(
            ProductRepository products,
            CategoryRepository categories,
            UnitRepository units,
            JdbcTemplate jdbc, Db database) {
        this.products = products;
        this.categories = categories;
        this.units = units;
        this.jdbc = jdbc;
        this.database = database;
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
        String code = internalCodeForNewProduct(request.code());
        if (products.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Já existe um produto com este código.");
        }
        return ProductResponse.from(
                products.save(
                        new Product(
                                code,
                                StringUtils.hasText(request.name()) ? request.name().trim()
                                        : "Material sem nome " + UUID.randomUUID().toString().substring(0, 8),
                                request.description(),
                                categoryOrDefault(request.categoryId()),
                                unitOrDefault(request.baseUnitCode()),
                                request.cost() == null ? BigDecimal.ZERO : request.cost(),
                                request.price() == null ? BigDecimal.ZERO : request.price())));
    }

    public ProductResponse update(UUID id, ProductRequest request) {
        var locked =
                jdbc.query(
                        "SELECT id FROM product WHERE id = ? FOR UPDATE",
                        (rs, row) -> rs.getObject(1, UUID.class),
                        id);
        if (locked.isEmpty()) throw new NotFoundException("Produto não encontrado.");
        Product product = find(id);
        Unit unit = StringUtils.hasText(request.baseUnitCode())
                ? findUnit(request.baseUnitCode()) : product.getBaseUnit();
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
        String code = StringUtils.hasText(request.code())
                ? normalizeCode(request.code()) : product.getCode();
        if (products.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new ConflictException("Já existe um produto com este código.");
        }
        product.update(
                code,
                StringUtils.hasText(request.name()) ? request.name().trim() : product.getName(),
                request.description(),
                request.categoryId() == null ? product.getCategory() : findCategory(request.categoryId()),
                unit,
                request.cost() == null ? product.getCost() : request.cost(),
                request.price() == null ? product.getPrice() : request.price());
        return ProductResponse.from(product);
    }

    public void deactivate(UUID id) {
        find(id).deactivate();
    }

    public ProductResponse reactivate(UUID id) {
        Product product = find(id);
        product.activate();
        return ProductResponse.from(product);
    }

    /** Importa materiais revisados, de modo transacional, sem mexer no saldo do estoque. */
    public Map<String, Integer> importReviewed(Actor actor, List<BulkItem> items) {
        if (items == null || items.isEmpty() || items.size() > 200)
            throw new BusinessRuleException("Envie de 1 a 200 materiais por lote.");
        var seen = new java.util.HashSet<String>();
        var prepared = new java.util.ArrayList<BulkItem>();
        // Validar tudo antes da primeira gravação: um item inválido não produz lote parcial.
        for (BulkItem item : items) {
            if (item == null || !StringUtils.hasText(item.name()) || item.name().trim().length() > 160)
                throw new BusinessRuleException("Todo material do lote precisa de nome (até 160 caracteres).");
            validatePrice(item.price());
            validatePrice(item.cost());
            if (StringUtils.hasText(item.unit()) && item.unit().length() > 12)
                throw new BusinessRuleException("Unidade inválida para " + item.name());
            prepared.add(item);
        }
        int created = 0, skipped = 0;
        for (BulkItem item : prepared) {
            String name = item.name().trim();
            String normalized = name.toLowerCase(Locale.ROOT);
            if (!seen.add(normalized) || products.existsByNameIgnoreCase(name)) {
                skipped++;
                continue;
            }
            create(new ProductRequest(null, name, null, null,
                    item.unit(), item.cost(), item.price()));
            created++;
        }
        database.audit(actor, "PRODUCT_BULK_IMPORT", null,
                "Importação conferida: " + created + " criado(s), " + skipped + " já existente(s)/repetido(s)");
        return Map.of("created", created, "skipped", skipped, "total", items.size());
    }

    public record BulkItem(String name, BigDecimal price, BigDecimal cost, String unit) {}

    private void validatePrice(BigDecimal value) {
        if (value != null && (value.signum() < 0 || value.scale() > 2
                || value.precision() - value.scale() > 12))
            throw new BusinessRuleException("Valor inválido: use preço/custo positivo com até 2 casas.");
    }

    private Product find(UUID id) {
        return products.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("Produto não encontrado."));
    }

    private Category categoryOrDefault(UUID id) {
        if (id != null) return findCategory(id);
        return categories.findByNameIgnoreCaseAndActiveTrue("Sem categoria")
                .orElseGet(() -> categories.findAllByActiveTrueOrderByNameAsc().stream()
                .findFirst().orElseThrow(() ->
                        new ConflictException("Nenhuma categoria ativa disponível para cadastrar materiais.")));
    }

    private Unit unitOrDefault(String code) {
        if (StringUtils.hasText(code)) return findUnit(code);
        return units.findById("UN")
                .orElseGet(() -> units.findAllByActiveTrueOrderByNameAsc().stream()
                    .findFirst().orElseThrow(() ->
                        new ConflictException("Nenhuma unidade ativa disponível para cadastrar materiais.")));
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

    private String internalCodeForNewProduct(String provided) {
        // Mantém identificador estável para o histórico, sem exigir código no formulário.
        if (StringUtils.hasText(provided)) return normalizeCode(provided);
        return "INT-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
