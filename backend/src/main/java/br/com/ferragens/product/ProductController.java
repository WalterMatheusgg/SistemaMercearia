package br.com.ferragens.product;

import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'STOCKKEEPER')")
public class ProductController {

    private final ProductService products;
    private final CategoryRepository categories;
    private final UnitRepository units;

    public ProductController(ProductService products, CategoryRepository categories, UnitRepository units) {
        this.products = products;
        this.categories = categories;
        this.units = units;
    }

    @GetMapping("/products")
    public PageResponse<ProductResponse> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.clamp(size, 1, 100);
        return PageResponse.from(products.search(query, active,
                PageRequest.of(boundedPage, boundedSize, Sort.by("name").ascending())));
    }

    @GetMapping("/products/{id}")
    public ProductResponse get(@PathVariable UUID id) {
        return products.get(id);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return products.create(request);
    }

    @PutMapping("/products/{id}")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return products.update(id, request);
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) {
        products.deactivate(id);
    }

    @GetMapping("/categories")
    public List<LookupResponse> categories() {
        return categories.findAllByActiveTrueOrderByNameAsc().stream()
                .map(category -> new LookupResponse(category.getId().toString(), category.getName())).toList();
    }

    @GetMapping("/units")
    public List<LookupResponse> units() {
        return units.findAllByActiveTrueOrderByNameAsc().stream()
                .map(unit -> new LookupResponse(unit.getCode(), unit.getName())).toList();
    }

    public record LookupResponse(String code, String name) {
    }
}