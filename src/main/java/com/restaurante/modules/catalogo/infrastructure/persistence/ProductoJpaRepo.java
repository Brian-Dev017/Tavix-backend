package com.restaurante.modules.catalogo.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductoJpaRepo extends JpaRepository<ProductoEntity, Long> {
    List<ProductoEntity> findByCategoriaIdOrderByNombreAsc(Long categoriaId);
    List<ProductoEntity> findByCategoriaIdAndDisponibleTrue(Long categoriaId);
    List<ProductoEntity> findByDisponibleTrue();
}
