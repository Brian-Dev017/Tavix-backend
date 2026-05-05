package com.restaurante.modules.admin.infrastructure.web.dto;

import java.math.BigDecimal;

public record GuardarProductoRequest(
        Long categoriaId,
        String nombre,
        String descripcion,
        BigDecimal precio,
        String imagenUrl,
        boolean disponible
) {}
