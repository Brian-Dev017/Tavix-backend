package com.restaurante.shared.audit;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "auditoria_validacion")
public class AuditoriaValidacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String modulo;

    @Column(nullable = false, length = 80)
    private String accion;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "rol_id", length = 2)
    private String rolId;

    @Column(name = "referencia_id")
    private Long referenciaId;

    @Column(nullable = false, length = 10)
    private String resultado;

    @Column(nullable = false, length = 255)
    private String detalle;

    @Column(columnDefinition = "TEXT")
    private String datos;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    public Long getId() { return id; }
    public String getModulo() { return modulo; }
    public String getAccion() { return accion; }
    public Long getUsuarioId() { return usuarioId; }
    public String getRolId() { return rolId; }
    public Long getReferenciaId() { return referenciaId; }
    public String getResultado() { return resultado; }
    public String getDetalle() { return detalle; }
    public String getDatos() { return datos; }
    public LocalDateTime getCreadoEn() { return creadoEn; }

    public void setModulo(String modulo) { this.modulo = modulo; }
    public void setAccion(String accion) { this.accion = accion; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    public void setRolId(String rolId) { this.rolId = rolId; }
    public void setReferenciaId(Long referenciaId) { this.referenciaId = referenciaId; }
    public void setResultado(String resultado) { this.resultado = resultado; }
    public void setDetalle(String detalle) { this.detalle = detalle; }
    public void setDatos(String datos) { this.datos = datos; }
}
