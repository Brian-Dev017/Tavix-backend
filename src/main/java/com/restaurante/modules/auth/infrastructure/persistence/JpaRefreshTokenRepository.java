package com.restaurante.modules.auth.infrastructure.persistence;

import com.restaurante.modules.auth.domain.port.out.RefreshTokenRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface JpaRefreshTokenRepository
        extends JpaRepository<RefreshTokenEntity, Long>, RefreshTokenRepositoryPort {

    Optional<RefreshTokenEntity> findByToken(String token);

    @Modifying
    @Transactional
    @Query("UPDATE RefreshTokenEntity r SET r.revocado = true WHERE r.token = :token")
    void revokarByToken(String token);

    @Modifying
    @Transactional
    @Query("UPDATE RefreshTokenEntity r SET r.revocado = true WHERE r.usuarioId = :usuarioId")
    void revokarByUsuarioId(Long usuarioId);

    default void save(Long usuarioId, String token, LocalDateTime expiraEn) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setUsuarioId(usuarioId);
        entity.setToken(token);
        entity.setExpiraEn(expiraEn);
        saveAndFlush(entity);
    }

    default Optional<Long> findUsuarioIdByToken(String token) {
        return findByToken(token).map(RefreshTokenEntity::getUsuarioId);
    }

    default boolean isTokenValido(String token) {
        return findByToken(token)
                .map(t -> !t.isRevocado() && t.getExpiraEn().isAfter(LocalDateTime.now()))
                .orElse(false);
    }

    default void revokar(String token) {
        revokarByToken(token);
    }

    default void revokarPorUsuario(Long usuarioId) {
        revokarByUsuarioId(usuarioId);
    }
}
