package com.restaurante.modules.admin.infrastructure.web.dto;

import java.math.BigDecimal;

public record ProductoAdminDTO(
        Long id,
        Long categoriaId,
        String categoriaNombre,
        String nombre,
        String descripcion,
        BigDecimal precio,
        String imagenUrl,
        boolean disponible
) {}
