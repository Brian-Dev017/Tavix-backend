package com.restaurante.modules.configuracion.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NegocioConfigJpaRepo extends JpaRepository<NegocioConfigEntity, Long> {
}
