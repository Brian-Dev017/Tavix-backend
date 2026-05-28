package com.restaurante.shared.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "auditoria_global")
public class AuditoriaGlobalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String modulo;

    @Column(name = "tabla_nombre", nullable = false, length = 100)
    private String tablaNombre;

    @Column(name = "registro_id", nullable = false, length = 64)
    private String registroId;

    @Column(nullable = false, length = 30)
    private String accion;

    @Column(length = 255)
    private String descripcion;

    @Column(name = "valor_anterior", columnDefinition = "json")
    private String valorAnterior;

    @Column(name = "valor_nuevo", columnDefinition = "json")
    private String valorNuevo;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "usuario_login", length = 100)
    private String usuarioLogin;

    @Column(name = "rol_id", length = 20)
    private String rolId;

    @Column(name = "ip_origen", length = 45)
    private String ipOrigen;

    @Column(length = 255)
    private String endpoint;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    public Long getId() { return id; }
    public String getModulo() { return modulo; }
    public String getTablaNombre() { return tablaNombre; }
    public String getRegistroId() { return registroId; }
    public String getAccion() { return accion; }
    public String getDescripcion() { return descripcion; }
    public String getValorAnterior() { return valorAnterior; }
    public String getValorNuevo() { return valorNuevo; }
    public Long getUsuarioId() { return usuarioId; }
    public String getUsuarioLogin() { return usuarioLogin; }
    public String getRolId() { return rolId; }
    public String getIpOrigen() { return ipOrigen; }
    public String getEndpoint() { return endpoint; }
    public LocalDateTime getCreadoEn() { return creadoEn; }

    public void setId(Long id) { this.id = id; }
    public void setModulo(String modulo) { this.modulo = modulo; }
    public void setTablaNombre(String tablaNombre) { this.tablaNombre = tablaNombre; }
    public void setRegistroId(String registroId) { this.registroId = registroId; }
    public void setAccion(String accion) { this.accion = accion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public void setValorAnterior(String valorAnterior) { this.valorAnterior = valorAnterior; }
    public void setValorNuevo(String valorNuevo) { this.valorNuevo = valorNuevo; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    public void setUsuarioLogin(String usuarioLogin) { this.usuarioLogin = usuarioLogin; }
    public void setRolId(String rolId) { this.rolId = rolId; }
    public void setIpOrigen(String ipOrigen) { this.ipOrigen = ipOrigen; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
}
