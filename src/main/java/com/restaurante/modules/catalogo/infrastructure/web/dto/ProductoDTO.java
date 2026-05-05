package com.restaurante.modules.catalogo.infrastructure.web.dto;

import java.math.BigDecimal;

public record ProductoDTO(Long id, String nombre, String descripcion,
                          BigDecimal precio, String imagenUrl) {}
