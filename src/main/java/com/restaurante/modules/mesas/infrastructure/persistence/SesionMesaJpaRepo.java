package com.restaurante.modules.mesas.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SesionMesaJpaRepo extends JpaRepository<SesionMesaEntity, Long> {
    Optional<SesionMesaEntity> findByMesaIdAndCerradaEnIsNull(Long mesaId);
}
