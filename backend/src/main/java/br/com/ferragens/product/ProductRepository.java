package br.com.ferragens.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    boolean existsByCodeIgnoreCase(String code);
    boolean existsByNameIgnoreCase(String name);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    @Override
    @EntityGraph(attributePaths = {"category", "baseUnit"})
    Page<Product> findAll(Specification<Product> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "baseUnit"})
    Optional<Product> findWithDetailsById(UUID id);
}