package com.restaurante.modules.caja.infrastructure.web.dto;

import java.math.BigDecimal;
import java.util.List;
import com.restaurante.modules.pedidos.infrastructure.web.dto.ItemPedidoDTO;

public record PedidoResumenDTO(
        Long pedidoId,
        String mesa,
        String mesero,
        int totalItems,
        BigDecimal subtotal,
        BigDecimal igv,
        BigDecimal totalConIgv,
        String estadoPedido,
        List<ItemPedidoDTO> items
) {}
