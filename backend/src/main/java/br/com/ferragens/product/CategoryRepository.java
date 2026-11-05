package br.com.ferragens.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    List<Category> findAllByActiveTrueOrderByNameAsc();
    Optional<Category> findByNameIgnoreCaseAndActiveTrue(String name);
}