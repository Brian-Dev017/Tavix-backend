package com.restaurante.modules.caja.infrastructure.web.dto;

public record DatosComprobanteRequest(
        String rucDni,
        String razonSocial,
        String direccion
) {}
