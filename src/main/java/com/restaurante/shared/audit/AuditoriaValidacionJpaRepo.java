package com.restaurante.shared.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaValidacionJpaRepo extends JpaRepository<AuditoriaValidacionEntity, Long> {
}
