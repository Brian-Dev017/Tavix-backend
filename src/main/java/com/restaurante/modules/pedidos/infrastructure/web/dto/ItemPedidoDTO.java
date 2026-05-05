package com.restaurante.modules.pedidos.infrastructure.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ItemPedidoDTO(
        Long detalleId,
        Long pedidoId,
        Long productoId,
        String productoNombre,
        int cantidad,
        BigDecimal precio,
        BigDecimal subtotal,
        String estado,
        String observaciones,
        LocalDateTime creadoEn
) {}
