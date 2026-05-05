package com.restaurante.modules.caja.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ComprobanteJpaRepo extends JpaRepository<ComprobanteEntity, Long> {
    Optional<ComprobanteEntity> findByPedidoId(Long pedidoId);
    Page<ComprobanteEntity> findByEstado(ComprobanteEntity.EstadoComprobante estado, Pageable pageable);
}
