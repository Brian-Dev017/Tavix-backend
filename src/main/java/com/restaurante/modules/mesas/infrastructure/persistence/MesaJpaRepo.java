package com.restaurante.modules.mesas.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MesaJpaRepo extends JpaRepository<MesaEntity, Long> {
    List<MesaEntity> findAllByOrderByNumeroAsc();
    Optional<MesaEntity> findFirstByTipo(MesaEntity.TipoMesa tipo);
}
