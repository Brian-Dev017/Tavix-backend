package com.restaurante.modules.mesas.infrastructure.web.dto;

public record MesaDTO(
        Long id,
        String numero,
        int capacidad,
        String estado,
        Long sesionId,
        Long meseroId,
        String meseroNombre
) {}
