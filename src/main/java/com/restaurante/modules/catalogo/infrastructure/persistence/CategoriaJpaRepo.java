package com.restaurante.modules.catalogo.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoriaJpaRepo extends JpaRepository<CategoriaEntity, Long> {
    List<CategoriaEntity> findByActivoTrue();
}
