package com.restaurante.modules.pedidos.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pedido")
public class PedidoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sesion_mesa_id")
    private Long sesionMesaId;

    @Enumerated(EnumType.STRING)
    private EstadoPedido estado = EstadoPedido.ABIERTO;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "motivo_cancelacion")
    private String motivoCancelacion;

    @Column(name = "cancelado_en")
    private LocalDateTime canceladoEn;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn = LocalDateTime.now();

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn = LocalDateTime.now();

    public enum EstadoPedido { ABIERTO, EN_COCINA, LISTO, COBRADO, CANCELADO }

    public Long getId() { return id; }
    public Long getSesionMesaId() { return sesionMesaId; }
    public EstadoPedido getEstado() { return estado; }
    public String getObservaciones() { return observaciones; }
    public String getMotivoCancelacion() { return motivoCancelacion; }
    public LocalDateTime getCanceladoEn() { return canceladoEn; }
    public LocalDateTime getCreadoEn() { return creadoEn; }

    public void setSesionMesaId(Long sesionMesaId) { this.sesionMesaId = sesionMesaId; }
    public void setEstado(EstadoPedido estado) { this.estado = estado; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public void setMotivoCancelacion(String motivoCancelacion) { this.motivoCancelacion = motivoCancelacion; }
    public void setCanceladoEn(LocalDateTime canceladoEn) { this.canceladoEn = canceladoEn; }
}
