package br.com.ferragens.product;

import br.com.ferragens.auth.CurrentActorService.Actor;
import br.com.ferragens.commerce.Db;
import br.com.ferragens.inventory.InventoryModels;
import br.com.ferragens.inventory.InventoryService;
import jakarta.persistence.EntityManager;
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
import java.text.Normalizer;
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
    private final InventoryService inventory;
    private final EntityManager entityManager;

    public ProductService(
            ProductRepository products,
            CategoryRepository categories,
            UnitRepository units,
            JdbcTemplate jdbc, Db database, InventoryService inventory, EntityManager entityManager) {
        this.products = products;
        this.categories = categories;
        this.units = units;
        this.jdbc = jdbc;
        this.database = database;
        this.inventory = inventory;
        this.entityManager = entityManager;
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
                                categoryOrSuggested(request.categoryId(), request.name()),
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
                request.categoryId() == null
                        ? suggestCategory(StringUtils.hasText(request.name()) ? request.name() : product.getName())
                        : findCategory(request.categoryId()),
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

    /** Importa materiais revisados e registra o saldo inicial, quando informado, em uma única transação. */
    public Map<String, Integer> importReviewed(Actor actor, List<BulkItem> items) {
        if (items == null || items.isEmpty() || items.size() > 200)
            throw new BusinessRuleException("Envie de 1 a 200 materiais por lote.");
        var seen = new java.util.HashSet<String>();
        var prepared = new java.util.ArrayList<BulkItem>();
        // Validar antes de gravar: qualquer erro desfaz integralmente o lote.
        for (BulkItem item : items) {
            if (item == null || (item.name() != null && item.name().trim().length() > 160))
                throw new BusinessRuleException("O nome pode ter no máximo 160 caracteres.");
            validatePrice(item.price());
            validatePrice(item.cost());
            if (StringUtils.hasText(item.unit()) && item.unit().length() > 12)
                throw new BusinessRuleException("Unidade inválida para " + item.name());
            validateQuantity(item.quantity(), item.unit());
            prepared.add(item);
        }
        // A importação usa um local cadastrado somente quando há quantidade positiva.
        boolean hasQuantity = prepared.stream().anyMatch(item ->
                item.quantity() != null && item.quantity().signum() > 0);
        var locations = hasQuantity ? inventory.locations(actor) : List.<InventoryModels.Location>of();
        if (hasQuantity && locations.isEmpty())
            throw new BusinessRuleException("Nenhum local de estoque ativo para registrar a quantidade inicial.");

        int created = 0, skipped = 0;
        for (BulkItem item : prepared) {
            String name = StringUtils.hasText(item.name()) ? item.name().trim()
                    : "Material sem nome " + UUID.randomUUID().toString().substring(0, 8);
            String normalized = name.toLowerCase(Locale.ROOT);
            if (!seen.add(normalized) || products.existsByNameIgnoreCase(name)) {
                skipped++;
                continue;
            }
            ProductResponse product = create(new ProductRequest(null, name, null, null,
                    item.unit(), item.cost(), item.price()));
            BigDecimal quantity = item.quantity() == null ? BigDecimal.ZERO : item.quantity();
            if (quantity.signum() > 0) {
                // O inventário usa JDBC e exige que o produto JPA já tenha sido persistido.
                entityManager.flush();
                inventory.execute(actor, UUID.randomUUID(), new InventoryModels.Command(
                        product.id(), locations.getFirst().id(), InventoryModels.Kind.INITIAL,
                        quantity, "Quantidade inicial do cadastro em lote", null, null));
            }
            created++;
        }
        database.audit(actor, "PRODUCT_BULK_IMPORT", null,
                "Importação conferida: " + created + " criado(s), " + skipped + " já existente(s)/repetido(s)");
        return Map.of("created", created, "skipped", skipped, "total", items.size());
    }

    /** Omitir qualquer campo significa usar os valores padrão; estoque inicial padrão é zero. */
    public record BulkItem(String name, BigDecimal price, BigDecimal cost, String unit,
                           BigDecimal quantity) {}

    private void validateQuantity(BigDecimal quantity, String unit) {
        if (quantity == null) return;
        if (quantity.signum() < 0 || quantity.scale() > 4
                || quantity.precision() - quantity.scale() > 15)
            throw new BusinessRuleException("Quantidade inválida. Informe um valor não negativo.");
        if ((!StringUtils.hasText(unit) || "UN".equalsIgnoreCase(unit.trim()))
                && quantity.stripTrailingZeros().scale() > 0)
            throw new BusinessRuleException("Para unidade padrão, informe quantidade inteira (ex.: 10).");
    }

    private void validatePrice(BigDecimal value) {
        if (value != null && (value.signum() < 0 || value.scale() > 2
                || value.precision() - value.scale() > 12))
            throw new BusinessRuleException("Valor inválido: use preço/custo positivo com até 2 casas.");
    }

    private Product find(UUID id) {
        return products.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("Produto não encontrado."));
    }

    /** "Escolher automaticamente" analisa o nome, mas nunca inventa uma categoria.
     * Se a associação não for segura, usa Sem categoria (não a primeira da lista).
     */
    public Category suggestCategory(String name) {
        var available = categories.findAllByActiveTrueOrderByNameAsc();
        String material = normalized(name);
        if (!material.isBlank()) {
            for (CategoryRule rule : CATEGORY_RULES) {
                if (rule.materialTerms().stream().anyMatch(term -> material.contains(term))) {
                    for (String alias : rule.categoryTerms()) {
                        for (Category candidate : available) {
                            if (normalized(candidate.getName()).contains(alias))
                                return candidate;
                        }
                    }
                    break; // Não inferir outra categoria se a indicada não estiver cadastrada.
                }
            }
            // O próprio nome do material também pode mencionar o nome de uma categoria cadastrada.
            for (Category candidate : available) {
                String categoryName = normalized(candidate.getName());
                if (categoryName.length() >= 5 && !categoryName.equals("sem categoria")
                        && material.contains(categoryName)) return candidate;
            }
        }
        return available.stream()
                .filter(category -> normalized(category.getName()).equals("sem categoria"))
                .findFirst()
                .orElseThrow(() -> new ConflictException(
                        "Cadastre ou reative a categoria 'Sem categoria' para produtos não classificados."));
    }

    private Category categoryOrSuggested(UUID id, String productName) {
        return id != null ? findCategory(id) : suggestCategory(productName);
    }

    private static String normalized(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").trim();
    }

    private record CategoryRule(List<String> materialTerms, List<String> categoryTerms) {}

    private static final List<CategoryRule> CATEGORY_RULES = List.of(
            new CategoryRule(List.of("furadeira", "parafusadeira", "esmerilhadeira", "lixadeira",
                    "martelete", "serra circular", "politriz", "soprador termico", "tupia"),
                    List.of("ferramenta eletr", "eletroportatil", "ferramenta a bateria")),
            new CategoryRule(List.of("parafuso", "porca", "arruela", "bucha", "prego",
                    "rebite", "chumbador", "barra roscada"),
                    List.of("fixacao", "parafuso", "ferragem")),
            new CategoryRule(List.of("torneira", "cano", "tubo pvc", "joelho", "sifao",
                    "registro", "luva soldavel", "conexao", "valvula hidraulica"),
                    List.of("hidraul", "encanamento", "tubulac")),
            new CategoryRule(List.of("tomada", "interruptor", "disjuntor", "lampada",
                    "fio eletrico", "cabo eletrico", "eletroduto", "fita isolante", "plugue"),
                    List.of("eletric", "material eletr", "instalac eletr")),
            new CategoryRule(List.of("martelo", "alicate", "chave de fenda", "chave philips",
                    "chave de boca", "chave allen", "serrote", "trena", "estilete", "nivel de bolha"),
                    List.of("ferramenta manual", "ferramenta")),
            new CategoryRule(List.of("tinta", "pincel", "rolo de pintura", "verniz",
                    "massa corrida", "solvente", "bandeja de pintura"),
                    List.of("pintura", "tintas")),
            new CategoryRule(List.of("capacete de seguranca", "oculos de protecao",
                    "luva de protecao", "protetor auricular", "mascara de protecao"),
                    List.of("epi", "protecao", "seguranca"))
    );

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
