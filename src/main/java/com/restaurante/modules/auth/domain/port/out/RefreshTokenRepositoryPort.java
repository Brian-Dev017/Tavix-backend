package com.restaurante.modules.auth.domain.port.out;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepositoryPort {
    void save(Long usuarioId, String token, LocalDateTime expiraEn);
    Optional<Long> findUsuarioIdByToken(String token);
    boolean isTokenValido(String token);
    void revokar(String token);
    void revokarPorUsuario(Long usuarioId);
}
