package com.restaurante.modules.caja.infrastructure.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ComprobanteResponseDTO(
        Long id,
        Long pedidoId,
        String tipoComprobante,
        String metodoPago,
        BigDecimal subtotal,
        BigDecimal igv,
        BigDecimal total,
        String estado,
        LocalDateTime pagadoEn
) {}
