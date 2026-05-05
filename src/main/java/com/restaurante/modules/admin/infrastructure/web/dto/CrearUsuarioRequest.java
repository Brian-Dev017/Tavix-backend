package com.restaurante.modules.admin.infrastructure.web.dto;

public record CrearUsuarioRequest(
        String nombre,
        String apellido,
        String usuario,
        String contrasena,
        String rolId
) {}
