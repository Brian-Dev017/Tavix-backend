package com.restaurante.modules.caja.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ArqueoJpaRepo extends JpaRepository<ArqueoEntity, Long> {
    List<ArqueoEntity> findTop10ByOrderByAperturaEnDesc();
    Optional<ArqueoEntity> findTopByEstadoOrderByAperturaEnDesc(ArqueoEntity.EstadoArqueo estado);
    Optional<ArqueoEntity> findTopByCajeroIdAndEstadoOrderByAperturaEnDesc(Long cajeroId, ArqueoEntity.EstadoArqueo estado);
    List<ArqueoEntity> findByEstadoOrderByAperturaEnDesc(ArqueoEntity.EstadoArqueo estado);
    boolean existsByCajeroIdAndAperturaEnBetween(Long cajeroId, LocalDateTime desde, LocalDateTime hasta);
}
