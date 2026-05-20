package com.restaurante.modules.configuracion.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface SerieComprobanteJpaRepo extends JpaRepository<SerieComprobanteEntity, Long> {
    List<SerieComprobanteEntity> findByActivoTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SerieComprobanteEntity> findTopByTipoAndActivoTrueOrderByIdAsc(String tipo);
}
