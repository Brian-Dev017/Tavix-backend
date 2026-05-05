package com.restaurante.modules.cocina.infrastructure.web.dto;

import java.time.LocalDateTime;

public record ColaItemDTO(
        Long detalleId,
        Long pedidoId,
        String mesa,
        String producto,
        int cantidad,
        String observaciones,
        String estado,
        LocalDateTime solicitadoEn
) {}
