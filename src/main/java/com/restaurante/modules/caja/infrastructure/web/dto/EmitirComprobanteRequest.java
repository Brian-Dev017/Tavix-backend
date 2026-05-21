package com.restaurante.modules.caja.infrastructure.web.dto;

import java.math.BigDecimal;

public record EmitirComprobanteRequest(
        Long pedidoId,
        String tipoComprobanteId,
        String metodoPago,
        DatosComprobanteRequest datosComprobante,
        BigDecimal descuento,
        String motivoDescuento,
        BigDecimal efectivoRecibido
) {}
