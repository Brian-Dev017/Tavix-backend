package com.restaurante.modules.caja.infrastructure.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ComprobanteResponseDTO(
        Long id,
        Long pedidoId,
        String tipoComprobante,
        String serie,
        Integer numero,
        String metodoPago,
        String tipoComprobanteNombre,
        BigDecimal subtotal,
        BigDecimal igv,
        BigDecimal descuento,
        BigDecimal total,
        BigDecimal efectivoRecibido,
        BigDecimal vuelto,
        String estado,
        LocalDateTime pagadoEn
) {}
