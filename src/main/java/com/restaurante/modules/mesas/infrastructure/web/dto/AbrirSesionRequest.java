package com.restaurante.modules.mesas.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

public record AbrirSesionRequest(@NotNull Long mesaId) {}
