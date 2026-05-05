package com.restaurante.modules.catalogo.infrastructure.web.dto;

import java.util.List;

public record CategoriaConProductosDTO(Long id, String nombre, List<ProductoDTO> productos) {}
