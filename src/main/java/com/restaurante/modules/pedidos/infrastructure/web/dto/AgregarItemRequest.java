package com.restaurante.modules.pedidos.infrastructure.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AgregarItemRequest(
        @NotNull Long productoId,
        @Min(1) int cantidad,
        String observaciones
) {}
