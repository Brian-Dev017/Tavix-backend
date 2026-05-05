package com.restaurante.modules.admin.infrastructure.web.dto;

public record UsuarioAdminDTO(
        Long id,
        String nombre,
        String apellido,
        String usuario,
        String rolId,
        boolean activo
) {}
