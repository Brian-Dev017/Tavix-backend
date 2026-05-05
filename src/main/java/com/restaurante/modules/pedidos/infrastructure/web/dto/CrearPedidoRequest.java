package com.restaurante.modules.pedidos.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

public record CrearPedidoRequest(@NotNull Long sesionMesaId) {}
