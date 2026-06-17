package com.restaurante.modules.auth.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "refresh_token",
        indexes = {
                @Index(name = "uk_refresh_token_token", columnList = "token", unique = true),
                @Index(name = "idx_refresh_token_usuario_id", columnList = "usuario_id")
        }
)
public class RefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false, unique = true, length = 36)
    private String token;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(nullable = false)
    private boolean revocado = false;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getToken() { return token; }
    public LocalDateTime getExpiraEn() { return expiraEn; }
    public boolean isRevocado() { return revocado; }

    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    public void setToken(String token) { this.token = token; }
    public void setExpiraEn(LocalDateTime expiraEn) { this.expiraEn = expiraEn; }
    public void setRevocado(boolean revocado) { this.revocado = revocado; }
}
