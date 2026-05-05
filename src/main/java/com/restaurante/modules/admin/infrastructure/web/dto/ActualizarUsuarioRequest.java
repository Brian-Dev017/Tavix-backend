package com.restaurante.modules.admin.infrastructure.web.dto;

public record ActualizarUsuarioRequest(
        String nombre,
        String apellido,
        String rolId,
        Boolean activo
) {}
