package com.restaurante.modules.auth.infrastructure.web.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String rol,
        String nombre,
        String apellido
) {}
