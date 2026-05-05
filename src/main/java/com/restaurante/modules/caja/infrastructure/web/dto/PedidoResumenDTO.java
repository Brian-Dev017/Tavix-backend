package com.restaurante.modules.caja.infrastructure.web.dto;

import java.math.BigDecimal;

public record PedidoResumenDTO(
        Long pedidoId,
        String mesa,
        String mesero,
        int totalItems,
        BigDecimal subtotal,
        BigDecimal igv,
        BigDecimal totalConIgv,
        String estadoPedido
) {}
