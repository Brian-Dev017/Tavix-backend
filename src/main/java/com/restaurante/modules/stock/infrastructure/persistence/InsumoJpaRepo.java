package com.restaurante.modules.stock.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InsumoJpaRepo extends JpaRepository<InsumoEntity, Long> {
    List<InsumoEntity> findAllByOrderByNombreAsc();
    List<InsumoEntity> findByActivoTrue();
}
