package com.restaurante.modules.pedidos.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DetallePedidoJpaRepo extends JpaRepository<DetallePedidoEntity, Long> {
    List<DetallePedidoEntity> findByPedidoId(Long pedidoId);
}
