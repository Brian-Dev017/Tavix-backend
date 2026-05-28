package com.restaurante.shared.audit;

public record AuditoriaContexto(
        Long usuarioId,
        String usuarioLogin,
        String rolId,
        String ipOrigen,
        String endpoint
) {
}
