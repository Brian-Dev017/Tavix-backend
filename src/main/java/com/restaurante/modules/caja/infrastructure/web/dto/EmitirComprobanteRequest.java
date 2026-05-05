package com.restaurante.modules.caja.infrastructure.web.dto;

public record EmitirComprobanteRequest(
        Long pedidoId,
        String tipoComprobanteId,
        String metodoPago,
        DatosComprobanteRequest datosComprobante
) {}
