package br.com.ferragens.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UnitRepository extends JpaRepository<Unit, String> {
    List<Unit> findAllByActiveTrueOrderByNameAsc();
}