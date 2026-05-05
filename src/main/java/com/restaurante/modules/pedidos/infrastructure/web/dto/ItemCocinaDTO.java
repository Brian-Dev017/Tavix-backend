package com.restaurante.modules.pedidos.infrastructure.web.dto;

import java.time.LocalDateTime;

public record ItemCocinaDTO(
        Long detalleId,
        Long pedidoId,
        String mesa,
        String producto,
        int cantidad,
        String observaciones,
        LocalDateTime solicitadoEn
) {}
