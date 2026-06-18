package com.restaurante.modules.pedidos.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;

import java.util.Optional;

public interface PedidoJpaRepo extends JpaRepository<PedidoEntity, Long> {
    List<PedidoEntity> findBySesionMesaId(Long sesionMesaId);
    Optional<PedidoEntity> findTopBySesionMesaIdAndEstadoNotOrderByCreadoEnAsc(Long sesionMesaId, PedidoEntity.EstadoPedido estado);
    long countByEstadoIn(Collection<PedidoEntity.EstadoPedido> estados);
}
