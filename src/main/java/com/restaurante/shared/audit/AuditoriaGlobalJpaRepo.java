package com.restaurante.shared.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaGlobalJpaRepo extends JpaRepository<AuditoriaGlobalEntity, Long> {
}
