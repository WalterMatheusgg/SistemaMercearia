package br.com.ferragens.product;

import br.com.ferragens.shared.ConflictException;
import br.com.ferragens.shared.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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

    public ProductService(ProductRepository products, CategoryRepository categories, UnitRepository units) {
        this.products = products;
        this.categories = categories;
        this.units = units;
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(String query, Boolean active, Pageable pageable) {
        String normalizedQuery = StringUtils.hasText(query) ? query.trim() : null;
        Specification<Product> specification = (root, criteriaQuery, builder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (normalizedQuery != null) {
                String pattern = "%" + normalizedQuery.toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern),
                        builder.like(builder.lower(root.get("code")), pattern)));
            }
            if (active != null) {
                predicates.add(builder.equal(root.get("active"), active));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
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
        return ProductResponse.from(products.save(new Product(code, request.name().trim(), request.description(),
                findCategory(request.categoryId()), findUnit(request.baseUnitCode()), request.cost(), request.price())));
    }

    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = find(id);
        String code = normalizeCode(request.code());
        if (products.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new ConflictException("Já existe um produto com este código.");
        }
        product.update(code, request.name().trim(), request.description(), findCategory(request.categoryId()),
                findUnit(request.baseUnitCode()), request.cost(), request.price());
        return ProductResponse.from(product);
    }

    public void deactivate(UUID id) {
        find(id).deactivate();
    }

    private Product find(UUID id) {
        return products.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Produto não encontrado."));
    }

    private Category findCategory(UUID id) {
        return categories.findById(id).orElseThrow(() -> new NotFoundException("Categoria não encontrada."));
    }

    private Unit findUnit(String code) {
        return units.findById(code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("Unidade não encontrada."));
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}