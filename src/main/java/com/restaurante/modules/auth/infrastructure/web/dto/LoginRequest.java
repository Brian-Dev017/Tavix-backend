package com.restaurante.modules.auth.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "El usuario es requerido") String usuario,
        @NotBlank(message = "La contraseña es requerida") String contrasena
) {}
